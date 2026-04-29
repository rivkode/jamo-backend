package app.backend.jamo.diary.presentation.dto;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/v1/diaries/{diaryId}/like Request body.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §8 (명시적 boolean 멱등 — comment 정합).
 *
 * <p>{@code liked=true} → UPSERT (없으면 INSERT, 있으면 no-op) / {@code false} → DELETE (없으면 no-op).
 * 멱등 보장.
 *
 * @param liked 호출 후 원하는 상태 — null 차단 (Bean Validation, 명시적 boolean 멱등성 정책)
 */
public record ToggleDiaryLikeRequest(
    @NotNull(message = "liked is required")
    Boolean liked
) {
}
