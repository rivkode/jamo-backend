package app.backend.jamo.diary.application.dto.diary;

import java.util.Objects;
import java.util.UUID;

/**
 * 일기 좋아요 토글 use case 입력.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §8.
 *
 * <p>{@code liked} = true → UPSERT (없으면 INSERT, 있으면 no-op) / false → DELETE (없으면 no-op).
 * 멱등 보장.
 */
public record ToggleDiaryLikeCommand(UUID diaryId, UUID userId, boolean liked) {

    public ToggleDiaryLikeCommand {
        Objects.requireNonNull(diaryId, "diaryId");
        Objects.requireNonNull(userId, "userId");
    }
}
