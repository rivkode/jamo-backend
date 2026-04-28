package app.backend.jamo.identity.infrastructure.persistence.mapper;

import app.backend.jamo.identity.domain.model.profile.AvatarUrl;
import app.backend.jamo.identity.domain.model.profile.Bio;
import app.backend.jamo.identity.domain.model.profile.Locale;
import app.backend.jamo.identity.domain.model.profile.Profile;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.infrastructure.persistence.entity.ProfileJpaEntity;

/**
 * Profile aggregate ↔ ProfileJpaEntity 변환.
 *
 * <p>shared identifier 패턴 — Profile.id 와 JpaEntity.userId 가 같은 UUID 값.
 */
public final class ProfileMapper {

    private ProfileMapper() {
    }

    public static ProfileJpaEntity toJpaEntity(Profile profile) {
        return new ProfileJpaEntity(
                profile.id().value(),
                profile.bio().map(Bio::value).orElse(null),
                profile.avatarUrl().map(AvatarUrl::value).orElse(null),
                profile.locale().code(),
                profile.createdAt(),
                profile.updatedAt());
    }

    /**
     * 기존 row 갱신용 — JPA managed entity 의 mutable setter 를 통해 변경.
     */
    public static ProfileJpaEntity mergeInto(ProfileJpaEntity existing, Profile profile) {
        existing.setBio(profile.bio().map(Bio::value).orElse(null));
        existing.setAvatarUrl(profile.avatarUrl().map(AvatarUrl::value).orElse(null));
        existing.setLocale(profile.locale().code());
        existing.setUpdatedAt(profile.updatedAt());
        return existing;
    }

    public static Profile toDomain(ProfileJpaEntity entity) {
        Bio bio = entity.getBio() == null ? null : new Bio(entity.getBio());
        AvatarUrl avatarUrl = entity.getAvatarUrl() == null ? null : new AvatarUrl(entity.getAvatarUrl());
        Locale locale = new Locale(entity.getLocale());

        return Profile.restore(
                new UserId(entity.getUserId()),
                bio,
                avatarUrl,
                locale,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
