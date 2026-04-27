package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.AuthExchangeCommand;
import app.backend.jamo.identity.application.dto.AuthExchangeResult;
import app.backend.jamo.identity.domain.exception.AuthCodeExpiredException;
import app.backend.jamo.identity.domain.exception.AuthCodeNotFoundException;
import app.backend.jamo.identity.domain.model.auth.AuthorizationCode;
import app.backend.jamo.identity.domain.model.auth.RefreshTokenRecord;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.AuthorizationCodeStore;
import app.backend.jamo.identity.domain.repository.RefreshTokenStore;
import app.backend.jamo.identity.domain.service.RefreshTokenHasher;
import app.backend.jamo.identity.infrastructure.config.JwtProperties;
import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtIssuer;
import app.backend.jamo.common.auth.JwtTokenType;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Auth exchange use case (PRD auth/exchange.md).
 *
 * <p>흐름: authorizationCode atomic consume → 만료 검증 → access+refresh JWT 페어 발급
 * → refresh hash 만 Redis 보관 → 토큰 응답.
 *
 * <p>예외: {@link AuthCodeNotFoundException} (없거나 이미 사용됨) /
 * {@link AuthCodeExpiredException}. ExceptionHandler 가 401 매핑.
 */
@Service
public class AuthExchangeService {

    private final AuthorizationCodeStore authCodeStore;
    private final JwtIssuer jwtIssuer;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthExchangeService(AuthorizationCodeStore authCodeStore,
                               JwtIssuer jwtIssuer,
                               RefreshTokenHasher refreshTokenHasher,
                               RefreshTokenStore refreshTokenStore,
                               JwtProperties jwtProperties,
                               Clock clock) {
        this.authCodeStore = authCodeStore;
        this.jwtIssuer = jwtIssuer;
        this.refreshTokenHasher = refreshTokenHasher;
        this.refreshTokenStore = refreshTokenStore;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public AuthExchangeResult exchange(AuthExchangeCommand command) {
        AuthorizationCode authCode = authCodeStore.consume(command.code())
                .orElseThrow(() -> new AuthCodeNotFoundException(
                        "authorization code not found or already used"));

        Instant now = clock.instant();
        if (authCode.isExpired(now)) {
            throw new AuthCodeExpiredException("authorization code expired");
        }

        UserId userId = authCode.userId();
        String sessionId = authCode.sessionId();
        String deviceId = authCode.deviceId();

        Duration accessTtl = jwtProperties.accessTtl();
        Duration refreshTtl = jwtProperties.refreshTtl();

        String accessJwt = jwtIssuer.issue(new JwtClaims(
                userId.asString(), sessionId, deviceId,
                JwtTokenType.ACCESS, now, now.plus(accessTtl)
        ));
        String refreshJwt = jwtIssuer.issue(new JwtClaims(
                userId.asString(), sessionId, deviceId,
                JwtTokenType.REFRESH, now, now.plus(refreshTtl)
        ));

        String refreshHash = refreshTokenHasher.hash(refreshJwt);
        refreshTokenStore.store(new RefreshTokenRecord(
                userId, sessionId, deviceId, refreshHash, now, now.plus(refreshTtl)
        ));

        return new AuthExchangeResult(userId, accessJwt, refreshJwt, accessTtl.toSeconds());
    }
}
