package app.backend.jamo.identity.infrastructure.persistence.mapper;

import app.backend.jamo.identity.domain.model.oauth.OAuthIdentity;
import app.backend.jamo.identity.domain.model.oauth.OAuthIdentityId;
import app.backend.jamo.identity.domain.model.oauth.ProviderUserId;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.infrastructure.persistence.entity.OAuthIdentityJpaEntity;
import app.backend.jamo.identity.infrastructure.persistence.entity.UserJpaEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserJpaEntity toJpaEntity(User user) {
        UserJpaEntity entity = new UserJpaEntity(
                user.id().value(),
                user.displayName().value(),
                user.email().map(Email::value).orElse(null),
                user.createdAt(),
                user.updatedAt()
        );
        for (OAuthIdentity identity : user.oauthIdentities()) {
            OAuthIdentityJpaEntity child = new OAuthIdentityJpaEntity(
                    identity.id().value(),
                    identity.provider(),
                    identity.providerUserId().value(),
                    identity.createdAt()
            );
            entity.addOauthIdentity(child);
        }
        return entity;
    }

    public static UserJpaEntity mergeInto(UserJpaEntity entity, User user) {
        entity.setDisplayName(user.displayName().value());
        entity.setEmail(user.email().map(Email::value).orElse(null));
        entity.setUpdatedAt(user.updatedAt());

        Map<UUID, OAuthIdentityJpaEntity> existing = entity.getOauthIdentities().stream()
                .collect(Collectors.toMap(OAuthIdentityJpaEntity::getId, e -> e));
        for (OAuthIdentity identity : user.oauthIdentities()) {
            if (!existing.containsKey(identity.id().value())) {
                OAuthIdentityJpaEntity child = new OAuthIdentityJpaEntity(
                        identity.id().value(),
                        identity.provider(),
                        identity.providerUserId().value(),
                        identity.createdAt()
                );
                entity.addOauthIdentity(child);
            }
        }
        return entity;
    }

    public static User toDomain(UserJpaEntity entity) {
        Email email = entity.getEmail() != null ? new Email(entity.getEmail()) : null;
        List<OAuthIdentity> identities = entity.getOauthIdentities().stream()
                .map(UserMapper::toOAuthIdentity)
                .toList();
        return User.restore(
                new UserId(entity.getId()),
                new DisplayName(entity.getDisplayName()),
                email,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                identities
        );
    }

    public static OAuthIdentity toOAuthIdentity(OAuthIdentityJpaEntity entity) {
        return OAuthIdentity.restore(
                new OAuthIdentityId(entity.getId()),
                new UserId(entity.getUser().getId()),
                entity.getProvider(),
                new ProviderUserId(entity.getProviderUserId()),
                entity.getCreatedAt()
        );
    }
}
