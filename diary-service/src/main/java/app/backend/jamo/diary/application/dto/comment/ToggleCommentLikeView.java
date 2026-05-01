package app.backend.jamo.diary.application.dto.comment;

import java.util.Objects;
import java.util.UUID;

/**
 * 댓글 좋아요 토글 응답 view (3 필드, 박제 §8).
 *
 * <p>{@code liked} 는 호출 후 상태 — 일반적으로 요청과 동일 (멱등). 단 race window + UNIQUE catch fallback path
 * 에서는 DB 진실 (actualLiked) 을 노출 ({@code ToggleDiaryLikeService} 의 #81 cleanup 패턴 정합).
 * {@code likeCount} 는 호출 후 카운터 (즉시 반영).
 */
public record ToggleCommentLikeView(UUID commentId, boolean liked, int likeCount) {

    public ToggleCommentLikeView {
        Objects.requireNonNull(commentId, "commentId");
        if (likeCount < 0) {
            throw new IllegalArgumentException("likeCount must be non-negative");
        }
    }
}
