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
 * <p><b>mergeInto 범위</b>: {@code likeCount} / {@code commentCount} (Aggregate behavior) + {@code content} /
 * {@code images} / {@code tags} / {@code visibility} (Slice 3-a 작성자 수정, PRD 0526_flutter.md §2.4 — PUT
 * /diaries/{id}). {@code id} / {@code authorId} / {@code createdAt} 은 immutable.
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
     * 기존 row 갱신용 — JPA managed entity 의 mutable setter 호출.
     *
     * <p>Slice 3-a 부터 Aggregate.update 가 content / images / tags / visibility 를 변경하므로 본 메서드도
     * 4 필드 갱신을 포함한다. likeCount / commentCount 는 좋아요 / 댓글 mutator 와 함께 동기화.
     *
     * <p>{@code id} / {@code authorId} / {@code createdAt} 은 immutable — 본 메서드에서 갱신하지 않는다.
     * Aggregate 가 이들 값을 바꾸지 않는 invariant 와 정합.
     */
    public static DiaryJpaEntity mergeInto(DiaryJpaEntity existing, Diary diary) {
        existing.setContent(diary.content().value());
        existing.setImages(diary.images().values());
        existing.setTags(diary.tags().asStrings());
        existing.setVisibility(diary.visibility().name());
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
