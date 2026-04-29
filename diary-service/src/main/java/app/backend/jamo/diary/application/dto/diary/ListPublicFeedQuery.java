package app.backend.jamo.diary.application.dto.diary;

import app.backend.jamo.diary.domain.model.diary.DiaryFeedSort;
import app.backend.jamo.diary.domain.model.diary.Tag;
import app.backend.jamo.diary.domain.repository.cursor.PopularFeedCursor;
import app.backend.jamo.diary.domain.repository.cursor.RecentFeedCursor;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 공개 피드 조회 use case 입력.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §7.
 *
 * <p><b>cursor 양립</b>: sort 별 cursor 형식이 다르므로 두 cursor 모두 nullable. sort 가 RECENT 면
 * {@code recentCursor} 만 의미, POPULAR 면 {@code popularCursor} 만 의미. Presentation 단의 cursor codec 이
 * sort-specific 분기 후 적절한 cursor 만 채움.
 *
 * <p>{@code viewerId} 는 likedByMe 일괄 조회용 — 비로그인 미지원 (CLAUDE.md `auth=Y`).
 */
public record ListPublicFeedQuery(
    UUID viewerId,
    Optional<Tag> tag,
    DiaryFeedSort sort,
    Optional<RecentFeedCursor> recentCursor,
    Optional<PopularFeedCursor> popularCursor,
    int size
) {

    public ListPublicFeedQuery {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(sort, "sort");
        Objects.requireNonNull(recentCursor, "recentCursor");
        Objects.requireNonNull(popularCursor, "popularCursor");
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size out of range: 1..100, got " + size);
        }
    }
}
