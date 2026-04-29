package app.backend.jamo.diary.domain.repository.cursor;

import app.backend.jamo.diary.domain.model.diary.DiaryId;

import java.time.Instant;
import java.util.Objects;

/**
 * POPULAR 정렬 피드의 keyset 페이징 cursor.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §7.
 *
 * <p>{@code (like_count, created_at, diary_id)} 튜플. tiebreak: 동일 {@code like_count} 안에선
 * {@code created_at desc}, 동일 시각 안에선 {@code diary_id desc}. SQL 인덱스 강결합 → Repository port 의 detail
 * 이지 도메인 invariant 가 아니므로 {@code domain/repository/cursor} 에 위치.
 *
 * <p>{@code likeCount} 음수 불가 — invariant.
 */
public record PopularFeedCursor(int lastLikeCount, Instant lastCreatedAt, DiaryId lastDiaryId) {

    public PopularFeedCursor {
        if (lastLikeCount < 0) {
            throw new IllegalArgumentException("lastLikeCount must be non-negative");
        }
        Objects.requireNonNull(lastCreatedAt, "lastCreatedAt");
        Objects.requireNonNull(lastDiaryId, "lastDiaryId");
    }
}
