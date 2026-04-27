package app.backend.jamo.identity.domain.model.user;

import app.backend.jamo.identity.domain.model.oauth.OAuthIdentity;
import app.backend.jamo.identity.domain.model.oauth.OAuthIdentityId;
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

    @Test
    void register_local_creates_user_with_password_and_no_oauth() {
        User user = User.registerLocal(
                new DisplayName("jamo"),
                new Email("u@jamoai.app"),
                new HashedPassword("$2a$12$hash"),
                NOW
        );

        assertThat(user.id()).isNotNull();
        assertThat(user.accountType()).isEqualTo(AccountType.LOCAL);
        assertThat(user.hashedPassword()).isPresent();
        assertThat(user.hashedPassword().get().value()).isEqualTo("$2a$12$hash");
        assertThat(user.email()).isPresent();
        assertThat(user.email().get().value()).isEqualTo("u@jamoai.app");
        assertThat(user.oauthIdentities()).isEmpty();
        assertThat(user.createdAt()).isEqualTo(NOW);
    }

    @Test
    void register_local_rejects_null_email() {
        assertThatThrownBy(() -> User.registerLocal(
                new DisplayName("jamo"), null, new HashedPassword("$2a$12$h"), NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void register_local_rejects_null_password() {
        assertThatThrownBy(() -> User.registerLocal(
                new DisplayName("jamo"), new Email("u@jamoai.app"), null, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void register_with_oauth_has_oauth_account_type() {
        User user = User.registerWithOAuth(
                OAuthProvider.KAKAO, new ProviderUserId("k1"), new DisplayName("u"),
                new Email("u@k.com"), NOW);

        assertThat(user.accountType()).isEqualTo(AccountType.OAUTH);
        assertThat(user.hashedPassword()).isEmpty();
    }

    @Test
    void restore_rejects_local_without_password() {
        UserId id = UserId.generate();

        assertThatThrownBy(() -> User.restore(
                id, new DisplayName("u"), new Email("u@j.app"),
                AccountType.LOCAL, null, NOW, NOW, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LOCAL account requires hashedPassword");
    }

    @Test
    void restore_rejects_oauth_with_password() {
        UserId id = UserId.generate();
        OAuthIdentity identity = OAuthIdentity.restore(
                OAuthIdentityId.generate(),
                id, OAuthProvider.KAKAO, new ProviderUserId("k1"), NOW);

        assertThatThrownBy(() -> User.restore(
                id, new DisplayName("u"), new Email("u@k.com"),
                AccountType.OAUTH, new HashedPassword("$2a$12$h"),
                NOW, NOW, List.of(identity)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OAUTH account must not have hashedPassword");
    }

    @Test
    void restore_rejects_local_with_oauth_identities() {
        UserId id = UserId.generate();
        OAuthIdentity identity = OAuthIdentity.restore(
                OAuthIdentityId.generate(),
                id, OAuthProvider.KAKAO, new ProviderUserId("k1"), NOW);

        assertThatThrownBy(() -> User.restore(
                id, new DisplayName("u"), new Email("u@j.app"),
                AccountType.LOCAL, new HashedPassword("$2a$12$h"),
                NOW, NOW, List.of(identity)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LOCAL account must not have oauth identities");
    }

    @Test
    void restore_rejects_oauth_without_identity() {
        UserId id = UserId.generate();

        assertThatThrownBy(() -> User.restore(
                id, new DisplayName("u"), new Email("u@k.com"),
                AccountType.OAUTH, null, NOW, NOW, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OAUTH account requires at least one oauth identity");
    }
}
