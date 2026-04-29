package app.backend.jamo.diary.domain.model.diarylike;

import app.backend.jamo.diary.domain.model.diary.DiaryId;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * DiaryLike Aggregate Root — 사용자가 일기에 누른 좋아요 1건.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §8 (별도 Aggregate — 사용자 결정).
 *
 * <p><b>유니크 제약</b>: {@code (diaryId, userId)} 동시 INSERT 차단. {@link DiaryLikeId} 는 PK 이지만
 * 비즈니스 식별은 외래 ID 조합 — Repository 는 {@code findByDiaryIdAndUserId} / {@code deleteByDiaryIdAndUserId}
 * 로 멱등 UPSERT/DELETE 지원.
 *
 * <p><b>외래 ID (ADR-0005)</b>: {@code diaryId} / {@code userId} 는 raw 보유. {@link Diary} Aggregate 의
 * {@code likeCount} 카운터 동기화는 같은 트랜잭션 안에서 호출자 (Application Service) 책임.
 *
 * <p><b>비공개 가드</b>: 비공개 일기 + 비작성자 좋아요 시도는 Application Service 가 {@link Diary#isAccessibleBy}
 * 로 사전 차단 — 본 Aggregate 자체는 가드 미수행 (단일 책임).
 *
 * <p>본 Aggregate 는 상태 전이가 없는 단순 fact (CRUD) — UPSERT (create) / DELETE (toggle off) 만 존재.
 */
public final class DiaryLike {

    private final DiaryLikeId id;
    private final DiaryId diaryId;
    private final UUID userId;
    private final Instant createdAt;

    private DiaryLike(DiaryLikeId id, DiaryId diaryId, UUID userId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.diaryId = Objects.requireNonNull(diaryId, "diaryId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    // ============================================================
    // Factories
    // ============================================================

    /** 신규 좋아요 — Aggregate ID 신규 발급. */
    public static DiaryLike create(DiaryId diaryId, UUID userId, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return new DiaryLike(DiaryLikeId.newId(), diaryId, userId, clock.instant());
    }

    /** Repository 복원용. */
    public static DiaryLike reconstitute(DiaryLikeId id, DiaryId diaryId, UUID userId, Instant createdAt) {
        return new DiaryLike(id, diaryId, userId, createdAt);
    }

    // ============================================================
    // Getters
    // ============================================================

    public DiaryLikeId id() {
        return id;
    }

    public DiaryId diaryId() {
        return diaryId;
    }

    public UUID userId() {
        return userId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiaryLike that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
