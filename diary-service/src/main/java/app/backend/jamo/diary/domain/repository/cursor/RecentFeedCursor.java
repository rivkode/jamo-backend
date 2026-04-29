package app.backend.jamo.diary.domain.repository.cursor;

import app.backend.jamo.diary.domain.model.diary.DiaryId;

import java.time.Instant;
import java.util.Objects;

/**
 * RECENT 정렬 피드의 keyset 페이징 cursor.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §7.
 *
 * <p>{@code (created_at, diary_id)} 튜플로 SQL 인덱스 (`created_at desc, diary_id desc`) 와 1:1 대응.
 * Aggregate 의 일관성 경계나 비즈니스 invariant 가 아닌 <b>Repository port 의 페이지네이션 메커니즘</b> —
 * 따라서 {@code domain/model} 이 아닌 {@code domain/repository/cursor} 에 위치 (port 의 일부).
 *
 * <p>{@code listFeed (recent)} 와 {@code listMyFeed} 가 공유. 첫 페이지는 cursor null 로 호출자가 표현.
 * 인코딩 (base64) 은 Presentation/Application 책임 — 도메인은 의미만 보유.
 */
public record RecentFeedCursor(Instant lastCreatedAt, DiaryId lastDiaryId) {

    public RecentFeedCursor {
        Objects.requireNonNull(lastCreatedAt, "lastCreatedAt");
        Objects.requireNonNull(lastDiaryId, "lastDiaryId");
    }
}
