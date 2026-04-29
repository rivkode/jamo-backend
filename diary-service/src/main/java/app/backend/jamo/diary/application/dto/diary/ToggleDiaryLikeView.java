package app.backend.jamo.diary.application.dto.diary;

import java.util.Objects;
import java.util.UUID;

/**
 * 좋아요 토글 응답 view.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §8.
 *
 * <p>{@code liked} 는 호출 후 상태 (멱등 — 요청과 동일). {@code likeCount} 는 호출 후 카운터 (즉시 반영).
 */
public record ToggleDiaryLikeView(UUID diaryId, boolean liked, int likeCount) {

    public ToggleDiaryLikeView {
        Objects.requireNonNull(diaryId, "diaryId");
        if (likeCount < 0) {
            throw new IllegalArgumentException("likeCount must be non-negative");
        }
    }
}
