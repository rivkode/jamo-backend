package app.backend.jamo.identity.domain.service;

import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthAuthenticationRequestTest {

    @Test
    void creates_with_pkce_verifier() {
        OAuthAuthenticationRequest req = new OAuthAuthenticationRequest(
                OAuthProvider.GOOGLE, "auth-code-x", "https://app/callback", "verifier-y"
        );

        assertThat(req.pkceCodeVerifierOpt()).contains("verifier-y");
    }

    @Test
    void creates_without_pkce_verifier() {
        OAuthAuthenticationRequest req = new OAuthAuthenticationRequest(
                OAuthProvider.NAVER, "auth-code-x", "https://app/callback", null
        );

        assertThat(req.pkceCodeVerifierOpt()).isEqualTo(Optional.empty());
    }

    @Test
    void rejects_blank_authorization_code() {
        assertThatThrownBy(() -> new OAuthAuthenticationRequest(
                OAuthProvider.KAKAO, "  ", "https://app/callback", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authorizationCode");
    }

    @Test
    void rejects_blank_redirect_uri() {
        assertThatThrownBy(() -> new OAuthAuthenticationRequest(
                OAuthProvider.KAKAO, "code", "", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("redirectUri");
    }

    @Test
    void rejects_null_provider() {
        assertThatThrownBy(() -> new OAuthAuthenticationRequest(null, "code", "https://app/callback", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("provider");
    }
}
