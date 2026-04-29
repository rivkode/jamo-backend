package app.backend.jamo.diary.domain.repository;

import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.model.diary.Tag;
import app.backend.jamo.diary.domain.repository.cursor.PopularFeedCursor;
import app.backend.jamo.diary.domain.repository.cursor.RecentFeedCursor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Diary Aggregate Repository port.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §7 / §9 / §11 / §13. 구현체는 Infrastructure layer
 * ({@code DiaryRepositoryImpl}) — JpaEntity ↔ Domain Mapper 경유.
 *
 * <p><b>cleanup 메서드 ({@link #deleteAllByAuthorId})</b>: Saga 구독자 (회원 탈퇴 처리기) 만 호출 — 일반
 * Application Service 가 호출하지 않음. sentence-feedback 의 cascade 메서드 정합 (단일 Repository 인터페이스에 통합).
 */
public interface DiaryRepository {

    /** 신규 / 갱신 모두 동일 메서드 (UPSERT) — Aggregate ID 가 영속 키. */
    void save(Diary diary);

    Optional<Diary> findById(DiaryId id);

    boolean existsById(DiaryId id);

    /** 멱등 hard-delete (박제 §9) — 존재하지 않는 ID 호출은 no-op (구현체 강제). */
    void deleteById(DiaryId id);

    /**
     * 공개 피드 RECENT 정렬 — {@code WHERE visibility=PUBLIC} ({@code AND tag IN (...)} 옵션).
     *
     * @param tag         단일 태그 필터 (empty = 전체) — VO 통과로 도메인 invariant 보장
     * @param cursorOrNull null = 첫 페이지
     * @param limit       1..100
     */
    List<Diary> findPublicFeedRecent(Optional<Tag> tag, RecentFeedCursor cursorOrNull, int limit);

    /** 공개 피드 POPULAR 정렬 — tiebreak (created_at desc, diary_id desc). */
    List<Diary> findPublicFeedPopular(Optional<Tag> tag, PopularFeedCursor cursorOrNull, int limit);

    /** 본인 피드 RECENT — {@code WHERE author_id = userId} (visibility 무관). */
    List<Diary> findMyFeedRecent(UUID authorId, RecentFeedCursor cursorOrNull, int limit);

    /**
     * 회원 탈퇴 Saga cascade — sentence-feedback 정합 (Saga 구독자 only).
     *
     * <p>호출자: {@code UserWithdrawalRequestedListener} — {@code UserWithdrawalRequested} 구독 후 실행.
     * 처리 완료 시 {@code UserDataPurged (sourceService="diary")} 회신 발행. 일반 Application Service 가
     * 호출 시 메서드 시그니처상 권한 / 의도 식별이 어렵다 — 호출자 한정 강제.
     *
     * <p>멱등 — 존재하지 않는 authorId 호출은 no-op (return 0). Saga 재시도 안전.
     *
     * @return hard-delete 된 row 수
     */
    int deleteAllByAuthorId(UUID authorId);
}
