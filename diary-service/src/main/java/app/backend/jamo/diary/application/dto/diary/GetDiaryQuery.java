package app.backend.jamo.diary.application.dto.diary;

import java.util.Objects;
import java.util.UUID;

/**
 * 일기 단건 조회 use case 입력.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §2 (404 통일 IDOR) / §4 (likedByMe viewer-context).
 */
public record GetDiaryQuery(UUID diaryId, UUID viewerId) {

    public GetDiaryQuery {
        Objects.requireNonNull(diaryId, "diaryId");
        Objects.requireNonNull(viewerId, "viewerId");
    }
}
