package app.backend.jamo.diary.infrastructure.persistence.mapper;

import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diarylike.DiaryLike;
import app.backend.jamo.diary.domain.model.diarylike.DiaryLikeId;
import app.backend.jamo.diary.infrastructure.persistence.entity.DiaryLikeJpaEntity;

/**
 * DiaryLike Aggregate ↔ JpaEntity 변환. 단순 fact — toJpaEntity / toDomain 만 (mergeInto 없음, immutable).
 */
public final class DiaryLikeMapper {

    private DiaryLikeMapper() {
    }

    public static DiaryLikeJpaEntity toJpaEntity(DiaryLike like) {
        return new DiaryLikeJpaEntity(
            like.id().value(),
            like.diaryId().value(),
            like.userId(),
            like.createdAt()
        );
    }

    public static DiaryLike toDomain(DiaryLikeJpaEntity entity) {
        return DiaryLike.reconstitute(
            DiaryLikeId.of(entity.getId()),
            DiaryId.of(entity.getDiaryId()),
            entity.getUserId(),
            entity.getCreatedAt()
        );
    }
}
