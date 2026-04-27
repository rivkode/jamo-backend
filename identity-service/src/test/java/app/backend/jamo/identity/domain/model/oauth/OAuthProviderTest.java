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
}
