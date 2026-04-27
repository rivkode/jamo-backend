package app.backend.jamo.identity.infrastructure.persistence.mapper;

import app.backend.jamo.identity.domain.model.oauth.OAuthIdentity;
import app.backend.jamo.identity.domain.model.oauth.OAuthIdentityId;
import app.backend.jamo.identity.domain.model.oauth.ProviderUserId;
import app.backend.jamo.identity.domain.model.user.AccountType;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.infrastructure.persistence.entity.OAuthIdentityJpaEntity;
import app.backend.jamo.identity.infrastructure.persistence.entity.UserJpaEntity;

import java.util.List;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserJpaEntity toJpaEntity(User user) {
        return new UserJpaEntity(
                user.id().value(),
                user.displayName().value(),
                user.email().map(Email::value).orElse(null),
                user.createdAt(),
                user.updatedAt()
        );
    }

    public static UserJpaEntity mergeInto(UserJpaEntity entity, User user) {
        entity.setDisplayName(user.displayName().value());
        entity.setEmail(user.email().map(Email::value).orElse(null));
        entity.setUpdatedAt(user.updatedAt());
        return entity;
    }

    public static List<OAuthIdentityJpaEntity> toOAuthEntities(User user) {
        return user.oauthIdentities().stream()
                .map(UserMapper::toOAuthEntity)
                .toList();
    }

    public static OAuthIdentityJpaEntity toOAuthEntity(OAuthIdentity identity) {
        return new OAuthIdentityJpaEntity(
                identity.id().value(),
                identity.userId().value(),
                identity.provider(),
                identity.providerUserId().value(),
                identity.createdAt()
        );
    }

    public static User toDomain(UserJpaEntity user, List<OAuthIdentityJpaEntity> identities) {
        Email email = user.getEmail() != null ? new Email(user.getEmail()) : null;
        List<OAuthIdentity> domainIdentities = identities.stream()
                .map(UserMapper::toOAuthIdentity)
                .toList();
        // PR6-a 임시 매핑: account_type / password_hash 컬럼은 PR6-b 의 V3 마이그레이션과 함께 도입.
        // 본 슬라이스의 모든 기존 사용자는 OAuth 가입자이므로 OAUTH + null 로 복원.
        // 가드: 가정 위반 시 invariant 메시지보다 먼저 명시적으로 fail-fast (code review M1).
        if (domainIdentities.isEmpty()) {
            throw new IllegalStateException(
                    "user has no oauth identities — PR6-a 임시 매핑은 OAuth 사용자만 복원 가능. "
                            + "PR6-b 의 V3 마이그레이션 머지 후 account_type 컬럼 기반 분기로 교체 필수. userId="
                            + user.getId());
        }
        return User.restore(
                new UserId(user.getId()),
                new DisplayName(user.getDisplayName()),
                email,
                AccountType.OAUTH,
                null,
                user.getCreatedAt(),
                user.getUpdatedAt(),
                domainIdentities
        );
    }

    public static OAuthIdentity toOAuthIdentity(OAuthIdentityJpaEntity entity) {
        return OAuthIdentity.restore(
                new OAuthIdentityId(entity.getId()),
                new UserId(entity.getUserId()),
                entity.getProvider(),
                new ProviderUserId(entity.getProviderUserId()),
                entity.getCreatedAt()
        );
    }
}
