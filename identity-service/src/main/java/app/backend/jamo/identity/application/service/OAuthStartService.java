package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.OAuthStartCommand;
import app.backend.jamo.identity.application.dto.OAuthStartResult;
import app.backend.jamo.identity.domain.exception.UnsupportedOAuthProviderException;
import app.backend.jamo.identity.domain.model.auth.AuthState;
import app.backend.jamo.identity.domain.model.auth.OAuthFlowSession;
import app.backend.jamo.identity.domain.model.auth.PkceChallenge;
import app.backend.jamo.identity.domain.model.auth.PkceVerifier;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.repository.OAuthFlowSessionStore;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties.ProviderConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * OAuth start use case (PRD auth/start.md).
 *
 * <p>흐름: deviceId 결정 → state + PKCE verifier(?) 생성 → flowSession Redis 저장 →
 * authorize URL 빌드. 실제 cookie set / 302 응답은 Presentation 계층 책임.
 */
@Service
@RequiredArgsConstructor
public class OAuthStartService {

    private final OAuthProviderProperties properties;
    private final OAuthFlowSessionStore flowSessionStore;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public OAuthStartResult start(OAuthStartCommand command) {
        OAuthProvider provider = command.provider();
        ProviderConfig cfg = providerConfig(provider);

        String deviceId = command.deviceIdOpt().orElseGet(() -> "web-" + UUID.randomUUID());
        AuthState state = AuthState.random();
        PkceVerifier verifier = cfg.pkceEnabled() ? PkceVerifier.random(secureRandom) : null;

        Instant now = clock.instant();
        Duration ttl = properties.stateCookie().maxAge();
        OAuthFlowSession session = new OAuthFlowSession(
                state, provider, verifier, deviceId, cfg.redirectUri(),
                now, now.plus(ttl)
        );
        flowSessionStore.store(session);

        String authorizeUrl = buildAuthorizeUrl(cfg, state, verifier);
        return new OAuthStartResult(authorizeUrl, state, deviceId);
    }

    private ProviderConfig providerConfig(OAuthProvider provider) {
        ProviderConfig cfg = properties.providers().get(provider.name().toLowerCase(Locale.ROOT));
        if (cfg == null) {
            throw new UnsupportedOAuthProviderException("provider not configured: " + provider);
        }
        return cfg;
    }

    private String buildAuthorizeUrl(ProviderConfig cfg, AuthState state, PkceVerifier verifier) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(cfg.authorizeUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", cfg.clientId())
                .queryParam("redirect_uri", cfg.redirectUri())
                .queryParam("scope", cfg.scope())
                .queryParam("state", state.value());
        if (verifier != null) {
            PkceChallenge challenge = verifier.challenge();
            builder.queryParam("code_challenge", challenge.value())
                    .queryParam("code_challenge_method", "S256");
        }
        return builder.build().encode().toUriString();
    }
}
