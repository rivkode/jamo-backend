package app.backend.jamo.diary.infrastructure.persistence.mapper;

import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.model.commentlike.CommentLike;
import app.backend.jamo.diary.domain.model.commentlike.CommentLikeId;
import app.backend.jamo.diary.infrastructure.persistence.entity.CommentLikeJpaEntity;

/**
 * CommentLike Aggregate ↔ JpaEntity 변환. 단순 fact — mergeInto 없음.
 */
public final class CommentLikeMapper {

    private CommentLikeMapper() {
    }

    public static CommentLikeJpaEntity toJpaEntity(CommentLike like) {
        return new CommentLikeJpaEntity(
            like.id().value(),
            like.commentId().value(),
            like.userId(),
            like.createdAt()
        );
    }

    public static CommentLike toDomain(CommentLikeJpaEntity entity) {
        return CommentLike.reconstitute(
            CommentLikeId.of(entity.getId()),
            CommentId.of(entity.getCommentId()),
            entity.getUserId(),
            entity.getCreatedAt()
        );
    }
}
