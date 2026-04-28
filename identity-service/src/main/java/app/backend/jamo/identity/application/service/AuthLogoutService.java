package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.AuthLogoutCommand;
import app.backend.jamo.identity.domain.repository.RefreshTokenStore;
import app.backend.jamo.identity.domain.repository.SessionBlacklist;
import app.backend.jamo.identity.infrastructure.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Auth logout use case (PRD auth/logout.md §9).
 *
 * <p>단일 디바이스 logout: 현재 sid 만 access blacklist 에 등록(`bl:sid:{sid}`) 하고
 * refresh 보관도 함께 삭제. 다른 디바이스 sid 는 영향 없음.
 *
 * <p>blacklist TTL 은 {@link JwtProperties#blacklistTtl()} (= accessTtl + clockSkew) —
 * RsaJwtVerifier 의 clockSkew leeway 윈도우까지 거부 보장.
 *
 * <p><b>호출 순서</b>: blacklist 먼저, refresh delete 나중. blacklist 실패 시 RuntimeException
 * 으로 빠지면서 refresh 도 그대로 살아있어 클라이언트가 동일 access JWT 로 재시도 가능
 * (idempotent). 반대 순서는 blacklist 실패 시 refresh 만 사라져 사용자 락아웃 위험.
 *
 * <p><b>입력 신뢰</b>: {@code AuthLogoutCommand} 의 userId/sessionId 는 PR4-c controller
 * 가 검증된 access JWT claims 에서 추출하여 전달한다. 본 service 는 임의 입력을 신뢰하므로
 * controller 외 직접 호출자 추가 시 권한 검증을 반드시 controller 에 둘 것.
 */
@Service
@RequiredArgsConstructor
public class AuthLogoutService {

    private final SessionBlacklist sessionBlacklist;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtProperties jwtProperties;

    public void logout(AuthLogoutCommand command) {
        sessionBlacklist.blacklist(command.sessionId(), jwtProperties.blacklistTtl());
        refreshTokenStore.delete(command.userId(), command.sessionId());
    }
}
