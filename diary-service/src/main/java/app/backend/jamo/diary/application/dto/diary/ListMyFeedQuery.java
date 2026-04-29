package app.backend.jamo.diary.application.dto.diary;

import app.backend.jamo.diary.domain.repository.cursor.RecentFeedCursor;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 본인 피드 조회 use case 입력.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §7 (recent only, public+private 둘 다).
 *
 * <p>본인 피드는 RECENT 단일 sort — popular sort 미노출.
 */
public record ListMyFeedQuery(
    UUID authorId,
    Optional<RecentFeedCursor> cursor,
    int size
) {

    public ListMyFeedQuery {
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(cursor, "cursor");
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size out of range: 1..100, got " + size);
        }
    }
}
