package app.backend.jamo.diary.application.dto.diary;

import java.util.Objects;
import java.util.UUID;

/**
 * 공개 피드 조회 use case 입력.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §7.
 *
 * <p><b>책임 재배치 (cleanup PR — code-reviewer M1/M5)</b>: tag/cursor/sort 는 raw String (nullable) 로
 * 받는다. 도메인 VO ({@code Tag}) / cursor codec 호출 / {@code DiaryFeedSort} enum 변환은 Application
 * Service 책임 — Presentation 은 단순 raw String 전달만. invariant 위반 시 {@code InvalidTagException} /
 * {@code InvalidDiaryFeedCursorException} / {@code IllegalArgumentException} 모두 ExceptionHandler 가
 * 400 매핑.
 *
 * <p>{@code viewerId} 는 likedByMe 일괄 조회용 — 비로그인 미지원 (CLAUDE.md `auth=Y`).
 *
 * @param viewerId    호출자 user id (likedByMe 일괄 조회 키)
 * @param tagOrNull   raw tag (nullable / blank → no filter)
 * @param sortOrNull  raw sort (nullable → default RECENT, "recent"/"popular" case-insensitive)
 * @param cursorOrNull raw base64 cursor (nullable / blank → 첫 페이지). sort 별 형식 다름
 * @param size        페이지 크기 (1..100, Bean Validation 1차 + invariant 2차)
 */
public record ListPublicFeedQuery(
    UUID viewerId,
    String tagOrNull,
    String sortOrNull,
    String cursorOrNull,
    int size
) {

    public ListPublicFeedQuery {
        Objects.requireNonNull(viewerId, "viewerId");
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size out of range: 1..100, got " + size);
        }
    }
}
