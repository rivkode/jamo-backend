package app.backend.jamo.identity.application.service;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtExpiredException;
import app.backend.jamo.common.auth.JwtIssuer;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerificationException;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.identity.application.dto.AuthExchangeResult;
import app.backend.jamo.identity.application.dto.AuthRefreshCommand;
import app.backend.jamo.identity.domain.exception.RefreshTokenExpiredException;
import app.backend.jamo.identity.domain.exception.RefreshTokenInvalidException;
import app.backend.jamo.identity.domain.exception.RefreshTokenReuseDetectedException;
import app.backend.jamo.identity.domain.model.auth.RefreshTokenRecord;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.RefreshTokenStore;
import app.backend.jamo.identity.domain.repository.SessionBlacklist;
import app.backend.jamo.identity.domain.service.RefreshTokenHasher;
import app.backend.jamo.identity.domain.service.SessionIdGenerator;
import app.backend.jamo.identity.infrastructure.config.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Auth refresh use case (PRD auth/refresh.md §9).
 *
 * <p>회전형 refresh: 매 호출 시 신규 sessionId + 신규 토큰 페어 발급, 구 sid 즉시 폐기 +
 * 구 sid 도 access blacklist 등록 (구 access JWT 즉시 거부, 보안 boundary 단축).
 *
 * <p>reuse detection: 클라이언트가 보낸 refresh JWT 의 sessionId 가 store 에 없거나
 * hash 가 일치하지 않으면 탈취 의심 — 해당 user 의 모든 sid 를 blacklist 등록 + 일괄 삭제
 * 후 {@link RefreshTokenReuseDetectedException} 던짐 (보상 트랜잭션 best-effort).
 *
 * <p><b>트랜잭션 경계 / 부분 실패</b>: 본 service 는 Redis 만 다루므로 {@code @Transactional}
 * 미보유. 회전 happy path 는 (1) 신규 record store → (2) 구 sid blacklist → (3) 구 record
 * delete 순서. atomic 하지 않으므로 단계 사이 Redis 장애 시 다음 윈도우 가능:
 * <ul>
 *   <li>(1) 실패 — 신규 발급 자체 실패. 클라이언트 재시도 OK. 부수 상태 변화 없음.</li>
 *   <li>(2) 실패 — 클라이언트는 이미 신규 페어 수신. 구 access 가 만료까지 사용 가능 (보안
 *       boundary 약화 윈도우 = accessTtl). 운영 모니터링 + 메트릭 필요.</li>
 *   <li>(3) 실패 — 구 record 잔존하지만 (2) 가 성공했다면 blacklist 로 거부. record 는
 *       refresh JWT 만료 시 Redis 자동 정리.</li>
 * </ul>
 * <b>완전 atomic</b> 이 필요해지면 Redis Lua/MULTI 또는 distributed lock 도입 검토
 * (decisions/auth/refresh-rotation-blacklist-ports.md D6).
 *
 * <p><b>예외 분기</b>:
 * <ul>
 *   <li>{@link RefreshTokenExpiredException} — refresh JWT exp 만료 (정상 라이프사이클)</li>
 *   <li>{@link RefreshTokenInvalidException} — 위조/서명 실패/tokenType 불일치</li>
 *   <li>{@link RefreshTokenReuseDetectedException} — 폐기된 sid 재사용 (탈취 의심)</li>
 * </ul>
 *
 * <p><b>보상 트랜잭션 위치</b>: reuse 보상은 외부 효과 port 두 개의 sid 별 호출 — 도메인
 * 불변식 부재 + JwtProperties 역의존 회피로 Domain Service 가 아닌 Application Service
 * 에 위치. 동일 절차의 추가 호출자 (관리자 강제 로그아웃 등) 가 등장하면 도메인 서비스
 * 추출 재고 (Rule of Three).
 */
@Service
public class AuthRefreshService {

    private static final Logger log = LoggerFactory.getLogger(AuthRefreshService.class);
    private static final int SID_LOG_PREFIX_LENGTH = 8;

    private final JwtVerifier jwtVerifier;
    private final JwtIssuer jwtIssuer;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenStore refreshTokenStore;
    private final SessionBlacklist sessionBlacklist;
    private final SessionIdGenerator sessionIdGenerator;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthRefreshService(JwtVerifier jwtVerifier,
                              JwtIssuer jwtIssuer,
                              RefreshTokenHasher refreshTokenHasher,
                              RefreshTokenStore refreshTokenStore,
                              SessionBlacklist sessionBlacklist,
                              SessionIdGenerator sessionIdGenerator,
                              JwtProperties jwtProperties,
                              Clock clock) {
        this.jwtVerifier = jwtVerifier;
        this.jwtIssuer = jwtIssuer;
        this.refreshTokenHasher = refreshTokenHasher;
        this.refreshTokenStore = refreshTokenStore;
        this.sessionBlacklist = sessionBlacklist;
        this.sessionIdGenerator = sessionIdGenerator;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public AuthExchangeResult refresh(AuthRefreshCommand command) {
        JwtClaims claims = verifyRefreshJwt(command.refreshTokenJwt());
        UserId userId = UserId.fromString(claims.subject());
        String oldSessionId = claims.sessionId();
        String deviceId = claims.deviceId();

        verifyStoredHashOrTriggerReuse(userId, oldSessionId, command.refreshTokenJwt());

        Instant now = clock.instant();
        Duration accessTtl = jwtProperties.accessTtl();
        Duration refreshTtl = jwtProperties.refreshTtl();
        Duration blacklistTtl = jwtProperties.blacklistTtl();
        String newSessionId = sessionIdGenerator.newSessionId();

        String accessJwt = jwtIssuer.issue(new JwtClaims(
                userId.asString(), newSessionId, deviceId,
                JwtTokenType.ACCESS, now, now.plus(accessTtl)
        ));
        String refreshJwt = jwtIssuer.issue(new JwtClaims(
                userId.asString(), newSessionId, deviceId,
                JwtTokenType.REFRESH, now, now.plus(refreshTtl)
        ));

        refreshTokenStore.store(new RefreshTokenRecord(
                userId, newSessionId, deviceId, refreshTokenHasher.hash(refreshJwt),
                now, now.plus(refreshTtl)
        ));

        revokeOldSession(userId, oldSessionId, blacklistTtl);

        return new AuthExchangeResult(userId, accessJwt, refreshJwt, accessTtl.toSeconds());
    }

    private JwtClaims verifyRefreshJwt(String refreshTokenJwt) {
        JwtClaims claims;
        try {
            claims = jwtVerifier.verify(refreshTokenJwt);
        } catch (JwtExpiredException e) {
            throw new RefreshTokenExpiredException("refresh token expired");
        } catch (JwtVerificationException e) {
            throw new RefreshTokenInvalidException("refresh token verification failed");
        }
        if (claims.tokenType() != JwtTokenType.REFRESH) {
            throw new RefreshTokenInvalidException("token is not a refresh token");
        }
        return claims;
    }

    private void verifyStoredHashOrTriggerReuse(UserId userId, String sessionId, String refreshTokenJwt) {
        Optional<RefreshTokenRecord> stored = refreshTokenStore.find(userId, sessionId);
        String submittedHash = refreshTokenHasher.hash(refreshTokenJwt);
        boolean valid = stored.map(r -> constantTimeEquals(r.tokenHash(), submittedHash)).orElse(false);
        if (!valid) {
            triggerReuseCompensation(userId);
            throw new RefreshTokenReuseDetectedException(
                    "refresh token reuse detected for sessionId=" + maskSid(sessionId));
        }
    }

    private void triggerReuseCompensation(UserId userId) {
        Set<String> allSids = refreshTokenStore.findAllSessionIds(userId);
        Duration blacklistTtl = jwtProperties.blacklistTtl();
        int failures = 0;
        for (String sid : allSids) {
            try {
                sessionBlacklist.blacklist(sid, blacklistTtl);
                refreshTokenStore.delete(userId, sid);
            } catch (RuntimeException e) {
                failures++;
                log.error("reuse compensation partial failure userId={} sidPrefix={} error={}",
                        userId.asString(), maskSid(sid), e.getClass().getSimpleName(), e);
            }
        }
        if (!allSids.isEmpty() && failures == allSids.size()) {
            throw new IllegalStateException(
                    "reuse compensation completely failed for userId=" + userId.asString());
        }
        log.error("refresh token reuse detected userId={} totalSids={} failures={}",
                userId.asString(), allSids.size(), failures);
    }

    private void revokeOldSession(UserId userId, String oldSessionId, Duration blacklistTtl) {
        sessionBlacklist.blacklist(oldSessionId, blacklistTtl);
        refreshTokenStore.delete(userId, oldSessionId);
    }

    /**
     * HMAC hash 비교를 constant-time 으로 수행 — timing oracle 공격 표준 방어
     * (CWE-208, OWASP Cryptographic Storage Cheat Sheet).
     */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private static String maskSid(String sid) {
        if (sid == null) {
            return "<null>";
        }
        return sid.length() <= SID_LOG_PREFIX_LENGTH
                ? sid
                : sid.substring(0, SID_LOG_PREFIX_LENGTH) + "...";
    }
}
