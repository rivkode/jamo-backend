package app.backend.jamo.identity.infrastructure.persistence.mapper;

import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.ProviderUserId;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.infrastructure.persistence.entity.UserJpaEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private static final Instant NOW = Instant.parse("2026-04-27T10:00:00Z");

    @Test
    void to_jpa_entity_preserves_user_fields_and_identity() {
        User user = User.registerWithOAuth(
                OAuthProvider.KAKAO,
                new ProviderUserId("k1"),
                new DisplayName("jamo"),
                new Email("u@jamoai.app"),
                NOW
        );

        UserJpaEntity entity = UserMapper.toJpaEntity(user);

        assertThat(entity.getId()).isEqualTo(user.id().value());
        assertThat(entity.getDisplayName()).isEqualTo("jamo");
        assertThat(entity.getEmail()).isEqualTo("u@jamoai.app");
        assertThat(entity.getCreatedAt()).isEqualTo(NOW);
        assertThat(entity.getOauthIdentities()).hasSize(1);
        assertThat(entity.getOauthIdentities().get(0).getProvider()).isEqualTo(OAuthProvider.KAKAO);
        assertThat(entity.getOauthIdentities().get(0).getProviderUserId()).isEqualTo("k1");
        assertThat(entity.getOauthIdentities().get(0).getUser()).isSameAs(entity);
    }

    @Test
    void null_email_maps_to_null_column() {
        User user = User.registerWithOAuth(
                OAuthProvider.NAVER, new ProviderUserId("n1"), new DisplayName("u"), null, NOW
        );

        UserJpaEntity entity = UserMapper.toJpaEntity(user);

        assertThat(entity.getEmail()).isNull();
    }

    @Test
    void merge_into_appends_new_identities_and_updates_fields() {
        User user = User.registerWithOAuth(
                OAuthProvider.KAKAO, new ProviderUserId("k1"), new DisplayName("u"), null, NOW
        );
        UserJpaEntity persisted = UserMapper.toJpaEntity(user);

        Instant later = NOW.plusSeconds(60);
        user.linkOAuth(OAuthProvider.GOOGLE, new ProviderUserId("g1"), later);
        user.rename(new DisplayName("renamed"), later);

        UserJpaEntity merged = UserMapper.mergeInto(persisted, user);

        assertThat(merged.getDisplayName()).isEqualTo("renamed");
        assertThat(merged.getUpdatedAt()).isEqualTo(later);
        assertThat(merged.getOauthIdentities()).hasSize(2);
    }

    @Test
    void to_domain_round_trip_with_multiple_identities() {
        User user = User.registerWithOAuth(
                OAuthProvider.KAKAO, new ProviderUserId("k1"), new DisplayName("u"), null, NOW
        );
        user.linkOAuth(OAuthProvider.GOOGLE, new ProviderUserId("g1"), NOW.plusSeconds(60));

        UserJpaEntity entity = UserMapper.toJpaEntity(user);
        User restored = UserMapper.toDomain(entity);

        assertThat(restored.oauthIdentities()).hasSize(2);
        assertThat(restored.oauthIdentities())
                .extracting(o -> o.provider())
                .containsExactlyInAnyOrder(OAuthProvider.KAKAO, OAuthProvider.GOOGLE);
    }

    @Test
    void to_domain_round_trip_restores_user() {
        User original = User.registerWithOAuth(
                OAuthProvider.GOOGLE, new ProviderUserId("g1"), new DisplayName("u"),
                new Email("g@jamoai.app"), NOW
        );

        UserJpaEntity entity = UserMapper.toJpaEntity(original);
        User restored = UserMapper.toDomain(entity);

        assertThat(restored.id()).isEqualTo(original.id());
        assertThat(restored.displayName()).isEqualTo(original.displayName());
        assertThat(restored.email()).isEqualTo(original.email());
        assertThat(restored.oauthIdentities()).hasSize(1);
        assertThat(restored.oauthIdentities().get(0).provider()).isEqualTo(OAuthProvider.GOOGLE);
    }
}
