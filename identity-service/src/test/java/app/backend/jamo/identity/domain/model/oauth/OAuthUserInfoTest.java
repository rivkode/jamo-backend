package app.backend.jamo.identity.domain.model.oauth;

import app.backend.jamo.identity.domain.model.user.Email;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthUserInfoTest {

    private static final ProviderUserId PROVIDER_USER_ID = new ProviderUserId("12345678");

    @Test
    void of_creates_info_with_email() {
        OAuthUserInfo info = OAuthUserInfo.of(
                PROVIDER_USER_ID,
                "jamo-user",
                new Email("user@example.com")
        );

        assertThat(info.providerUserId()).isEqualTo(PROVIDER_USER_ID);
        assertThat(info.rawNickname()).isEqualTo("jamo-user");
        assertThat(info.email()).contains(new Email("user@example.com"));
    }

    @Test
    void without_email_creates_info_with_empty_email() {
        OAuthUserInfo info = OAuthUserInfo.withoutEmail(PROVIDER_USER_ID, "jamo-user");

        assertThat(info.email()).isEqualTo(Optional.empty());
    }

    @Test
    void rejects_null_provider_user_id() {
        assertThatThrownBy(() -> OAuthUserInfo.of(null, "nick", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("providerUserId");
    }

    @Test
    void rejects_blank_nickname() {
        assertThatThrownBy(() -> OAuthUserInfo.of(PROVIDER_USER_ID, "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rawNickname");
    }

    @Test
    void rejects_null_nickname() {
        assertThatThrownBy(() -> OAuthUserInfo.of(PROVIDER_USER_ID, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("rawNickname");
    }

    @Test
    void equals_compares_by_value() {
        OAuthUserInfo a = OAuthUserInfo.of(PROVIDER_USER_ID, "n", new Email("a@b.com"));
        OAuthUserInfo b = OAuthUserInfo.of(PROVIDER_USER_ID, "n", new Email("a@b.com"));

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
