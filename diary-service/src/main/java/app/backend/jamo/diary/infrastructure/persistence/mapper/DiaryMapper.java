package app.backend.jamo.diary.infrastructure.persistence.mapper;

import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryContent;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diary.ImageUrls;
import app.backend.jamo.diary.domain.model.diary.Tags;
import app.backend.jamo.diary.domain.model.diary.Visibility;
import app.backend.jamo.diary.infrastructure.persistence.entity.DiaryJpaEntity;

/**
 * Diary Aggregate ↔ JpaEntity 변환.
 *
 * <p>SentenceFeedbackMapper 패턴 정합 — toJpaEntity / mergeInto / toDomain 3 메서드.
 *
 * <p><b>mergeInto 범위</b>: {@code likeCount} / {@code commentCount} 만 갱신. 나머지 필드 (content / images /
 * tags / visibility / authorId / createdAt) 는 작성 후 immutable (박제 Non-Goals §editDiary).
 */
public final class DiaryMapper {

    private DiaryMapper() {
    }

    public static DiaryJpaEntity toJpaEntity(Diary diary) {
        return new DiaryJpaEntity(
            diary.id().value(),
            diary.authorId(),
            diary.content().value(),
            diary.images().values(),
            diary.tags().asStrings(),
            diary.visibility().name(),
            diary.likeCount(),
            diary.commentCount(),
            diary.createdAt()
        );
    }

    /**
     * 기존 row 갱신용 — JPA managed entity 의 mutable setter 호출. 카운터만 변경. Aggregate 의 모든 필드를
     * 외부에서 변경하지 않으므로 본 메서드는 카운터 sync 전용.
     */
    public static DiaryJpaEntity mergeInto(DiaryJpaEntity existing, Diary diary) {
        existing.setLikeCount(diary.likeCount());
        existing.setCommentCount(diary.commentCount());
        return existing;
    }

    public static Diary toDomain(DiaryJpaEntity entity) {
        return Diary.reconstitute(
            DiaryId.of(entity.getId()),
            entity.getAuthorId(),
            new DiaryContent(entity.getContent()),
            new ImageUrls(entity.getImages()),
            Tags.ofStrings(entity.getTags()),
            Visibility.valueOf(entity.getVisibility()),
            entity.getLikeCount(),
            entity.getCommentCount(),
            entity.getCreatedAt()
        );
    }
}
