package app.backend.jamo.diary.presentation.dto;

import app.backend.jamo.diary.application.dto.diary.ToggleDiaryLikeView;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.UUID;

/**
 * 일기 좋아요 토글 응답.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §8 ({@code likeCount} 즉시 반영).
 *
 * <p>{@code liked} 는 호출 후 상태 (요청과 동일 — 멱등). {@code likeCount} 는 호출 후 카운터.
 *
 * <p><b>PRD 0526_flutter.md §2.7 정합 (Slice 2)</b>: {@code userLiked} alias 동시 노출 — {@code liked} 동의어.
 */
public record ToggleDiaryLikeResponse(UUID diaryId, boolean liked, int likeCount) {

    public ToggleDiaryLikeResponse {
        Objects.requireNonNull(diaryId, "diaryId");
        if (likeCount < 0) {
            throw new IllegalArgumentException("likeCount must be non-negative");
        }
    }

    /** PRD §2.7 alias — liked 동의어. */
    @JsonProperty("userLiked")
    public boolean userLiked() {
        return liked;
    }

    public static ToggleDiaryLikeResponse from(ToggleDiaryLikeView view) {
        return new ToggleDiaryLikeResponse(view.diaryId(), view.liked(), view.likeCount());
    }
}
