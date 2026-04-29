package app.backend.jamo.diary.application.dto.diary;

import java.util.Objects;
import java.util.UUID;

/**
 * 일기 삭제 use case 입력.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §9 (작성자 only / 404 통일 / DiaryDeleted Saga cascade).
 */
public record DeleteDiaryCommand(UUID diaryId, UUID requesterId) {

    public DeleteDiaryCommand {
        Objects.requireNonNull(diaryId, "diaryId");
        Objects.requireNonNull(requesterId, "requesterId");
    }
}
