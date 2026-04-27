package app.backend.jamo.identity.domain.service;

import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;

import java.util.Objects;
import java.util.Optional;

/**
 * OAuth provider 의 token+userinfo 호출에 필요한 입력.
 * pkceCodeVerifier 는 ADR-0006 결정 1 에 따라 provider 별 선택 — 미사용 시 null.
 */
public record OAuthAuthenticationRequest(
        OAuthProvider provider,
        String authorizationCode,
        String redirectUri,
        String pkceCodeVerifier
) {
    public OAuthAuthenticationRequest {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(authorizationCode, "authorizationCode");
        if (authorizationCode.isBlank()) {
            throw new IllegalArgumentException("authorizationCode must not be blank");
        }
        Objects.requireNonNull(redirectUri, "redirectUri");
        if (redirectUri.isBlank()) {
            throw new IllegalArgumentException("redirectUri must not be blank");
        }
    }

    public Optional<String> pkceCodeVerifierOpt() {
        return Optional.ofNullable(pkceCodeVerifier);
    }
}
