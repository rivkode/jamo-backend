package app.backend.jamo.diary.infrastructure.persistence.mapper;

import app.backend.jamo.diary.domain.model.comment.Comment;
import app.backend.jamo.diary.domain.model.comment.CommentContent;
import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.infrastructure.persistence.entity.CommentJpaEntity;

/**
 * Comment Aggregate ↔ JpaEntity 변환.
 */
public final class CommentMapper {

    private CommentMapper() {
    }

    public static CommentJpaEntity toJpaEntity(Comment comment) {
        return new CommentJpaEntity(
            comment.id().value(),
            comment.diaryId().value(),
            comment.authorId(),
            comment.content().value(),
            comment.parentId().map(CommentId::value).orElse(null),
            comment.likeCount(),
            comment.createdAt()
        );
    }

    /**
     * 댓글은 edit use case 가 없으므로 denormalized likeCount 만 갱신한다.
     */
    public static CommentJpaEntity mergeInto(CommentJpaEntity existing, Comment comment) {
        existing.setLikeCount(comment.likeCount());
        return existing;
    }

    public static Comment toDomain(CommentJpaEntity entity) {
        CommentId parentId = entity.getParentId() == null
            ? null
            : CommentId.of(entity.getParentId());
        return Comment.reconstitute(
            CommentId.of(entity.getId()),
            DiaryId.of(entity.getDiaryId()),
            entity.getAuthorId(),
            new CommentContent(entity.getContent()),
            parentId,
            entity.getLikeCount(),
            entity.getCreatedAt()
        );
    }
}
