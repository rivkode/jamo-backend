package app.backend.jamo.diary.infrastructure.persistence.mapper;

import app.backend.jamo.diary.domain.model.audio.AudioClip;
import app.backend.jamo.diary.domain.model.audio.AudioClipId;
import app.backend.jamo.diary.infrastructure.persistence.entity.AudioClipJpaEntity;

/**
 * AudioClip 도메인 ↔ JpaEntity 변환.
 */
public final class AudioClipMapper {

    private AudioClipMapper() {
    }

    public static AudioClipJpaEntity toJpaEntity(AudioClip clip) {
        return new AudioClipJpaEntity(
            clip.id().value(),
            clip.ownerUserId(),
            clip.storedName(),
            clip.contentType(),
            clip.sizeBytes(),
            clip.createdAt()
        );
    }

    public static AudioClip toDomain(AudioClipJpaEntity entity) {
        return AudioClip.reconstitute(
            AudioClipId.of(entity.getId()),
            entity.getOwnerUserId(),
            entity.getStoredName(),
            entity.getContentType(),
            entity.getSizeBytes(),
            entity.getCreatedAt()
        );
    }
}
