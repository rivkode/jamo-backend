package app.backend.jamo.diary.presentation.dto;

import app.backend.jamo.diary.application.dto.comment.ToggleCommentLikeView;

import java.util.Objects;
import java.util.UUID;

/**
 * 댓글 좋아요 토글 응답 (PRD 0526_flutter.md §3.3).
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §8 ({@code likeCount} 즉시 반영).
 *
 * <p>{@code userLiked} ← Application {@link ToggleCommentLikeView#liked()} (호출 후 DB 진실).
 */
public record ToggleCommentLikeResponse(UUID commentId, int likeCount, boolean userLiked) {

    public ToggleCommentLikeResponse {
        Objects.requireNonNull(commentId, "commentId");
        if (likeCount < 0) {
            throw new IllegalArgumentException("likeCount must be non-negative");
        }
    }

    public static ToggleCommentLikeResponse from(ToggleCommentLikeView view) {
        return new ToggleCommentLikeResponse(view.commentId(), view.likeCount(), view.liked());
    }
}
