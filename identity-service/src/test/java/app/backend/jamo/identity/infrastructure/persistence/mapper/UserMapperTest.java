package app.backend.jamo.identity.infrastructure.persistence.mapper;

import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.ProviderUserId;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.infrastructure.persistence.entity.OAuthIdentityJpaEntity;
import app.backend.jamo.identity.infrastructure.persistence.entity.UserJpaEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private static final Instant NOW = Instant.parse("2026-04-27T10:00:00Z");

    @Test
    void to_jpa_entity_preserves_user_fields() {
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
    }

    @Test
    void to_oauth_entities_preserves_user_id_reference() {
        User user = User.registerWithOAuth(
                OAuthProvider.KAKAO, new ProviderUserId("k1"), new DisplayName("u"), null, NOW
        );

        List<OAuthIdentityJpaEntity> oauthEntities = UserMapper.toOAuthEntities(user);

        assertThat(oauthEntities).hasSize(1);
        OAuthIdentityJpaEntity child = oauthEntities.get(0);
        assertThat(child.getUserId()).isEqualTo(user.id().value());
        assertThat(child.getProvider()).isEqualTo(OAuthProvider.KAKAO);
        assertThat(child.getProviderUserId()).isEqualTo("k1");
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
    void merge_into_updates_user_fields_only() {
        User user = User.registerWithOAuth(
                OAuthProvider.KAKAO, new ProviderUserId("k1"), new DisplayName("u"), null, NOW
        );
        UserJpaEntity persisted = UserMapper.toJpaEntity(user);
        Instant later = NOW.plusSeconds(60);

        user.rename(new DisplayName("renamed"), later);
        user.updateEmail(new Email("new@jamoai.app"), later);

        UserJpaEntity merged = UserMapper.mergeInto(persisted, user);

        assertThat(merged.getDisplayName()).isEqualTo("renamed");
        assertThat(merged.getEmail()).isEqualTo("new@jamoai.app");
        assertThat(merged.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    void to_domain_with_multiple_identities() {
        User user = User.registerWithOAuth(
                OAuthProvider.KAKAO, new ProviderUserId("k1"), new DisplayName("u"), null, NOW
        );
        user.linkOAuth(OAuthProvider.GOOGLE, new ProviderUserId("g1"), NOW.plusSeconds(60));

        UserJpaEntity entity = UserMapper.toJpaEntity(user);
        List<OAuthIdentityJpaEntity> identityEntities = UserMapper.toOAuthEntities(user);

        User restored = UserMapper.toDomain(entity, identityEntities);

        assertThat(restored.oauthIdentities()).hasSize(2);
        assertThat(restored.oauthIdentities())
                .extracting(o -> o.provider())
                .containsExactlyInAnyOrder(OAuthProvider.KAKAO, OAuthProvider.GOOGLE);
    }

    @Test
    void to_domain_round_trip_restores_single_identity_user() {
        User original = User.registerWithOAuth(
                OAuthProvider.GOOGLE, new ProviderUserId("g1"), new DisplayName("u"),
                new Email("g@jamoai.app"), NOW
        );

        UserJpaEntity entity = UserMapper.toJpaEntity(original);
        List<OAuthIdentityJpaEntity> identities = UserMapper.toOAuthEntities(original);
        User restored = UserMapper.toDomain(entity, identities);

        assertThat(restored.id()).isEqualTo(original.id());
        assertThat(restored.displayName()).isEqualTo(original.displayName());
        assertThat(restored.email()).isEqualTo(original.email());
        assertThat(restored.oauthIdentities()).hasSize(1);
        assertThat(restored.oauthIdentities().get(0).provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(restored.oauthIdentities().get(0).userId()).isEqualTo(original.id());
    }
}
