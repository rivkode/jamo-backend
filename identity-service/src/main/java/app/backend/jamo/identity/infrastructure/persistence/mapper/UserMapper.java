package app.backend.jamo.identity.infrastructure.persistence.mapper;

import app.backend.jamo.identity.domain.model.oauth.OAuthIdentity;
import app.backend.jamo.identity.domain.model.oauth.OAuthIdentityId;
import app.backend.jamo.identity.domain.model.oauth.ProviderUserId;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.HashedPassword;
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
                user.accountType(),
                user.hashedPassword().map(HashedPassword::value).orElse(null),
                user.createdAt(),
                user.updatedAt()
        );
    }

    /**
     * mutable 필드만 갱신 (displayName, email, updatedAt).
     *
     * <p>현재 PR (PR6-b) 시점에는 비밀번호 변경 / accountType 전환 use case 가 없으므로
     * {@code accountType}, {@code passwordHash} 는 갱신 대상 외. 향후 비밀번호 리셋 또는
     * LOCAL+OAuth 하이브리드 도입 시 본 메서드 시그니처 + {@link UserJpaEntity} 의 setter 확장 필수.
     * (code review H1)
     */
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
        HashedPassword hashedPassword = user.getPasswordHash() != null
                ? new HashedPassword(user.getPasswordHash())
                : null;
        List<OAuthIdentity> domainIdentities = identities.stream()
                .map(UserMapper::toOAuthIdentity)
                .toList();
        return User.restore(
                new UserId(user.getId()),
                new DisplayName(user.getDisplayName()),
                email,
                user.getAccountType(),
                hashedPassword,
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
