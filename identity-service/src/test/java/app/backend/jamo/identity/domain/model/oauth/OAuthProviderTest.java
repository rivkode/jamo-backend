package app.backend.jamo.identity.domain.model.oauth;

import app.backend.jamo.identity.domain.exception.UnsupportedOAuthProviderException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthProviderTest {

    @Test
    void from_external_accepts_lowercase() {
        assertThat(OAuthProvider.fromExternal("kakao")).isEqualTo(OAuthProvider.KAKAO);
        assertThat(OAuthProvider.fromExternal("NAVER")).isEqualTo(OAuthProvider.NAVER);
        assertThat(OAuthProvider.fromExternal("Google")).isEqualTo(OAuthProvider.GOOGLE);
    }

    @Test
    void from_external_rejects_local() {
        assertThatThrownBy(() -> OAuthProvider.fromExternal("LOCAL"))
                .isInstanceOf(UnsupportedOAuthProviderException.class);
    }

    @Test
    void from_external_rejects_unknown() {
        assertThatThrownBy(() -> OAuthProvider.fromExternal("apple"))
                .isInstanceOf(UnsupportedOAuthProviderException.class);
    }

    @Test
    void requires_pkce_returns_true_for_kakao_and_google() {
        assertThat(OAuthProvider.KAKAO.requiresPkce()).isTrue();
        assertThat(OAuthProvider.GOOGLE.requiresPkce()).isTrue();
    }

    @Test
    void requires_pkce_returns_false_for_naver() {
        assertThat(OAuthProvider.NAVER.requiresPkce()).isFalse();
    }
}
