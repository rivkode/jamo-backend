package app.backend.jamo.identity.domain.model.user;

import app.backend.jamo.identity.domain.model.oauth.OAuthIdentity;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.ProviderUserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-04-27T10:00:00Z");

    @Test
    void register_with_oauth_creates_user_with_one_identity() {
        User user = User.registerWithOAuth(
                OAuthProvider.KAKAO,
                new ProviderUserId("kakao-12345"),
                new DisplayName("jamo"),
                new Email("user@jamoai.app"),
                NOW
        );

        assertThat(user.id()).isNotNull();
        assertThat(user.displayName().value()).isEqualTo("jamo");
        assertThat(user.email()).isPresent();
        assertThat(user.email().get().value()).isEqualTo("user@jamoai.app");
        assertThat(user.createdAt()).isEqualTo(NOW);
        assertThat(user.oauthIdentities()).hasSize(1);
        assertThat(user.oauthIdentities().get(0).provider()).isEqualTo(OAuthProvider.KAKAO);
    }

    @Test
    void register_without_email_is_allowed() {
        User user = User.registerWithOAuth(
                OAuthProvider.NAVER,
                new ProviderUserId("naver-1"),
                new DisplayName("ano"),
                null,
                NOW
        );

        assertThat(user.email()).isEmpty();
    }

    @Test
    void link_additional_oauth_appends_identity() {
        User user = User.registerWithOAuth(
                OAuthProvider.KAKAO, new ProviderUserId("k1"), new DisplayName("u"), null, NOW
        );
        Instant later = NOW.plusSeconds(60);

        OAuthIdentity google = user.linkOAuth(OAuthProvider.GOOGLE, new ProviderUserId("g1"), later);

        assertThat(user.oauthIdentities()).hasSize(2);
        assertThat(google.userId()).isEqualTo(user.id());
        assertThat(user.updatedAt()).isEqualTo(later);
    }

    @Test
    void linking_same_provider_twice_is_rejected() {
        User user = User.registerWithOAuth(
                OAuthProvider.KAKAO, new ProviderUserId("k1"), new DisplayName("u"), null, NOW
        );

        assertThatThrownBy(() ->
                user.linkOAuth(OAuthProvider.KAKAO, new ProviderUserId("k2"), NOW.plusSeconds(60))
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void update_email_replaces_value_and_timestamp() {
        User user = User.registerWithOAuth(
                OAuthProvider.KAKAO, new ProviderUserId("k1"), new DisplayName("u"), null, NOW
        );
        Instant later = NOW.plusSeconds(60);

        user.updateEmail(new Email("new@jamoai.app"), later);

        assertThat(user.email()).isPresent();
        assertThat(user.email().get().value()).isEqualTo("new@jamoai.app");
        assertThat(user.updatedAt()).isEqualTo(later);
    }

    @Test
    void rename_updates_display_name_and_timestamp() {
        User user = User.registerWithOAuth(
                OAuthProvider.KAKAO, new ProviderUserId("k1"), new DisplayName("u"), null, NOW
        );
        Instant later = NOW.plusSeconds(60);

        user.rename(new DisplayName("renamed"), later);

        assertThat(user.displayName().value()).isEqualTo("renamed");
        assertThat(user.updatedAt()).isEqualTo(later);
    }

    @Test
    void oauth_identities_are_returned_as_unmodifiable_view() {
        User user = User.registerWithOAuth(
                OAuthProvider.KAKAO, new ProviderUserId("k1"), new DisplayName("u"), null, NOW
        );

        List<OAuthIdentity> view = user.oauthIdentities();

        assertThatThrownBy(() -> view.add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}
