package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.OAuthCallbackCommand;
import app.backend.jamo.identity.application.dto.OAuthCallbackResult;
import app.backend.jamo.identity.domain.exception.OAuthFlowExpiredException;
import app.backend.jamo.identity.domain.exception.OAuthStateInvalidException;
import app.backend.jamo.identity.domain.model.auth.AuthorizationCode;
import app.backend.jamo.identity.domain.model.auth.AuthorizationCodeGenerator;
import app.backend.jamo.identity.domain.model.auth.OAuthFlowSession;
import app.backend.jamo.identity.domain.model.auth.PkceVerifier;
import app.backend.jamo.identity.domain.model.oauth.OAuthUserInfo;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.repository.AuthorizationCodeStore;
import app.backend.jamo.identity.domain.repository.OAuthFlowSessionStore;
import app.backend.jamo.identity.domain.service.OAuthAuthenticationRequest;
import app.backend.jamo.identity.domain.service.OAuthProviderClient;
import app.backend.jamo.identity.domain.service.SessionIdGenerator;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * OAuth callback use case (PRD auth/callback.md).
 *
 * <p>흐름:
 * <ol>
 *   <li>{@link #verifyAndConsumeFlowSession} — state cookie 검증 + Redis flowSession atomic GETDEL
 *       + 만료/provider 일관성 검증. 검증 실패 전이라도 GETDEL 은 일어나므로 state replay 차단.</li>
 *   <li>{@link OAuthProviderClient#authenticate} — 외부 HTTP (provider token + userinfo).</li>
 *   <li>{@link UserRegistrationService#findOrRegister} — DB 트랜잭션 (해당 service 내부의
 *       {@code @Transactional}). 본 service 는 트랜잭션 미보유 — 외부 IO 가 트랜잭션 안에
 *       묶이는 것을 회피 (security review H1).</li>
 *   <li>{@link #issueAuthorizationCode} — 자체 authorizationCode 발급 + Redis store.</li>
 * </ol>
 * SPA 가 이후 {@code POST /api/v1/auth/exchange} 로 토큰을 교환.
 *
 * <p>예외 정책:
 * <ul>
 *   <li>{@link OAuthStateInvalidException}: cookie state 부재/불일치 또는 provider 불일치</li>
 *   <li>{@link OAuthFlowExpiredException}: flowSession not found / expired (TTL 5분)</li>
 *   <li>OAuthAuthenticationException 계열: provider client 실패 — sanitize 후 그대로 propagate</li>
 * </ul>
 * Controller 가 try-catch 후 frontend redirect URL 로 매핑.
 */
@Service
public class OAuthCallbackService {

    private static final Logger log = LoggerFactory.getLogger(OAuthCallbackService.class);

    private final OAuthFlowSessionStore flowSessionStore;
    private final OAuthProviderClient providerClient;
    private final UserRegistrationService userRegistrationService;
    private final AuthorizationCodeGenerator authCodeGenerator;
    private final AuthorizationCodeStore authCodeStore;
    private final SessionIdGenerator sessionIdGenerator;
    private final OAuthProviderProperties properties;
    private final Clock clock;

    public OAuthCallbackService(OAuthFlowSessionStore flowSessionStore,
                                OAuthProviderClient providerClient,
                                UserRegistrationService userRegistrationService,
                                AuthorizationCodeGenerator authCodeGenerator,
                                AuthorizationCodeStore authCodeStore,
                                SessionIdGenerator sessionIdGenerator,
                                OAuthProviderProperties properties,
                                Clock clock) {
        this.flowSessionStore = flowSessionStore;
        this.providerClient = providerClient;
        this.userRegistrationService = userRegistrationService;
        this.authCodeGenerator = authCodeGenerator;
        this.authCodeStore = authCodeStore;
        this.sessionIdGenerator = sessionIdGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    public OAuthCallbackResult handle(OAuthCallbackCommand command) {
        OAuthFlowSession flowSession = verifyAndConsumeFlowSession(command);

        OAuthUserInfo userInfo = providerClient.authenticate(buildAuthRequest(command, flowSession));

        Instant now = clock.instant();
        UserRegistrationResult registration = userRegistrationService.findOrRegister(
                command.provider(), userInfo, now);

        String authCodeValue = issueAuthorizationCode(registration.user(), flowSession, now);

        log.info("oauth callback success provider={} userId={} isNew={} displayNameTruncated={}",
                command.provider(),
                registration.user().id().asString(),
                registration.isNewUser(),
                registration.displayNameTruncated());

        return new OAuthCallbackResult(
                authCodeValue, registration.isNewUser(), registration.displayNameTruncated());
    }

    /**
     * cookie state ↔ query state 일치 검증 → flowSession atomic GETDEL → 만료/provider 일관성 검증.
     * GETDEL 이 검증 단계 전 일어나는 것은 의도 — state replay 차단 (한 state 는 한 번만 사용).
     */
    private OAuthFlowSession verifyAndConsumeFlowSession(OAuthCallbackCommand command) {
        if (command.cookieStateOpt().isEmpty()
                || !command.cookieStateOpt().get().equals(command.receivedState())) {
            throw new OAuthStateInvalidException("state cookie missing or mismatched");
        }

        OAuthFlowSession flowSession = flowSessionStore.consume(command.receivedState())
                .orElseThrow(() -> new OAuthFlowExpiredException(
                        "flow session not found or already consumed"));

        if (flowSession.isExpired(clock.instant())) {
            throw new OAuthFlowExpiredException("flow session expired");
        }
        if (flowSession.provider() != command.provider()) {
            throw new OAuthStateInvalidException(
                    "provider mismatch — path=" + command.provider()
                            + " session=" + flowSession.provider());
        }
        return flowSession;
    }

    private OAuthAuthenticationRequest buildAuthRequest(OAuthCallbackCommand command,
                                                        OAuthFlowSession flowSession) {
        String pkceVerifierValue = flowSession.pkceVerifierOpt()
                .map(PkceVerifier::value)
                .orElse(null);
        return new OAuthAuthenticationRequest(
                command.provider(),
                command.code(),
                flowSession.redirectUri(),
                pkceVerifierValue
        );
    }

    private String issueAuthorizationCode(User user, OAuthFlowSession flowSession, Instant now) {
        String value = authCodeGenerator.generate();
        String sessionId = sessionIdGenerator.newSessionId();
        AuthorizationCode authCode = new AuthorizationCode(
                value,
                user.id(),
                sessionId,
                flowSession.deviceId(),
                now,
                now.plus(properties.authcodeTtl())
        );
        authCodeStore.store(authCode);
        return value;
    }
}
