package app.backend.jamo.identity.application.service;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtIssuer;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.identity.application.dto.AuthExchangeResult;
import app.backend.jamo.identity.application.dto.AuthLoginCommand;
import app.backend.jamo.identity.domain.exception.LoginInvalidException;
import app.backend.jamo.identity.domain.exception.LoginRateLimitedException;
import app.backend.jamo.identity.domain.model.auth.RefreshTokenRecord;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.HashedPassword;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.repository.LoginRateLimiter;
import app.backend.jamo.identity.domain.repository.RefreshTokenStore;
import app.backend.jamo.identity.domain.repository.PasswordEncoder;
import app.backend.jamo.identity.domain.repository.UserRepository;
import app.backend.jamo.identity.domain.service.RefreshTokenHasher;
import app.backend.jamo.identity.domain.service.SessionIdGenerator;
import app.backend.jamo.identity.infrastructure.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * LOCAL email/password 로그인 use case (PRD auth/login.md).
 *
 * <p>OAuth 로그인은 기존 {@code start → callback → exchange} 흐름을 사용한다. 본 service 는
 * LOCAL 계정만 조회하고, 성공 시 exchange 와 동일한 access/refresh JWT 페어를 발급한다.
 */
@Service
@RequiredArgsConstructor
public class AuthLoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter loginRateLimiter;
    private final JwtIssuer jwtIssuer;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenStore refreshTokenStore;
    private final SessionIdGenerator sessionIdGenerator;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AuthExchangeResult login(AuthLoginCommand command) {
        Instant now = clock.instant();
        Email email = new Email(command.email());
        String clientIp = command.clientIp();
        String deviceId = command.deviceId();

        if (!loginRateLimiter.isAllowed(email, clientIp, deviceId)) {
            throw new LoginRateLimitedException("login failure limit exceeded");
        }

        User user = userRepository.findLocalAccountByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matchesDummy(command.password());
            recordFailureAndReject(email, clientIp, deviceId);
        }

        HashedPassword hashedPassword = user.hashedPassword().orElse(null);
        if (hashedPassword == null) {
            passwordEncoder.matchesDummy(command.password());
            recordFailureAndReject(email, clientIp, deviceId);
        }
        if (!passwordEncoder.matches(command.password(), hashedPassword)) {
            recordFailureAndReject(email, clientIp, deviceId);
        }

        loginRateLimiter.reset(email, clientIp, deviceId);

        Duration accessTtl = jwtProperties.accessTtl();
        Duration refreshTtl = jwtProperties.refreshTtl();
        String sessionId = sessionIdGenerator.newSessionId();

        String accessJwt = jwtIssuer.issue(new JwtClaims(
                user.id().asString(), sessionId, deviceId,
                JwtTokenType.ACCESS, now, now.plus(accessTtl)
        ));
        String refreshJwt = jwtIssuer.issue(new JwtClaims(
                user.id().asString(), sessionId, deviceId,
                JwtTokenType.REFRESH, now, now.plus(refreshTtl)
        ));

        refreshTokenStore.store(new RefreshTokenRecord(
                user.id(), sessionId, deviceId,
                refreshTokenHasher.hash(refreshJwt), now, now.plus(refreshTtl)
        ));

        return new AuthExchangeResult(user.id(), accessJwt, refreshJwt, accessTtl.toSeconds());
    }

    private void recordFailureAndReject(Email email, String clientIp, String deviceId) {
        loginRateLimiter.recordFailure(email, clientIp, deviceId);
        throw new LoginInvalidException("invalid login credentials");
    }
}
