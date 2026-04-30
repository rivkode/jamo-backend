package app.backend.jamo.diary.domain.model.commentlike;

import java.util.Objects;
import java.util.UUID;

/**
 * CommentLike Aggregate 식별자 (UUID 래핑 VO).
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §1 / §8 (별도 Aggregate, DiaryLike 정합).
 *
 * <p>CommentLike 는 {@code Comment} 와 별도 Aggregate — 본 ID 는 {@code (commentId, userId)} 유니크 제약과 함께
 * PK 역할. 비즈니스 식별은 외래 ID 조합 — Repository 는 {@code findByCommentIdAndUserId} 로 멱등 UPSERT/DELETE.
 */
public record CommentLikeId(UUID value) {

    public CommentLikeId {
        Objects.requireNonNull(value, "value");
    }

    public static CommentLikeId newId() {
        return new CommentLikeId(UUID.randomUUID());
    }

    public static CommentLikeId of(UUID value) {
        return new CommentLikeId(value);
    }

    public static CommentLikeId fromString(String value) {
        Objects.requireNonNull(value, "value");
        try {
            return new CommentLikeId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid CommentLikeId: " + value, e);
        }
    }

    public String asString() {
        return value.toString();
    }
}
