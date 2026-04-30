package app.backend.jamo.diary.application.dto.diary;

import java.util.Objects;
import java.util.UUID;

/**
 * 본인 피드 조회 use case 입력.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §7 (recent only, public+private 둘 다).
 *
 * <p><b>책임 재배치 (cleanup PR — code-reviewer M1/M5)</b>: cursor 는 raw String (nullable). cursor codec
 * 호출은 Application Service 책임. RECENT 단일 sort — sort 옵션 미노출.
 *
 * @param authorId     본인 user id (= viewerId)
 * @param cursorOrNull raw base64 RECENT cursor (nullable / blank → 첫 페이지)
 * @param size         페이지 크기 (1..100)
 */
public record ListMyFeedQuery(
    UUID authorId,
    String cursorOrNull,
    int size
) {

    public ListMyFeedQuery {
        Objects.requireNonNull(authorId, "authorId");
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size out of range: 1..100, got " + size);
        }
    }
}
