package app.backend.jamo.diary.presentation.dto;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/v1/comments/{commentId}/like Request body.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §8 (boolean 명시 멱등).
 *
 * <p>{@code liked=true} → UPSERT (없으면 INSERT, 있으면 no-op) / {@code false} → DELETE (없으면 no-op).
 * {@link ToggleDiaryLikeRequest} 패턴 정합.
 *
 * @param liked 호출 후 원하는 상태 — null 차단 (명시적 boolean 멱등성 정책)
 */
public record ToggleCommentLikeRequest(
    @NotNull(message = "liked is required")
    Boolean liked
) {
}
