package app.backend.jamo.diary.application.dto.comment;

import java.util.Objects;
import java.util.UUID;

/**
 * 댓글 목록 조회 use case 입력.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §6 (chronological asc, size default 20 / max 100,
 * cursor opaque base64).
 *
 * <p><b>책임 재배치 (DiaryFeedCursorCodec / ListPublicFeedQuery 정합)</b>: cursor 는 raw String (nullable) 로
 * 받아 Application Service 가 codec 호출. invariant 위반 시 {@code InvalidCommentCursorException} 그대로 전파.
 *
 * @param diaryId       대상 일기
 * @param viewerId      호출자 user id (likedByMe 일괄 조회 키 + 비공개 일기 가드)
 * @param cursorOrNull  raw base64 cursor (nullable / blank → 첫 페이지)
 * @param size          페이지 크기 (1..100)
 */
public record ListCommentsQuery(
    UUID diaryId,
    UUID viewerId,
    String cursorOrNull,
    int size
) {

    public ListCommentsQuery {
        Objects.requireNonNull(diaryId, "diaryId");
        Objects.requireNonNull(viewerId, "viewerId");
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size out of range: 1..100, got " + size);
        }
    }
}
