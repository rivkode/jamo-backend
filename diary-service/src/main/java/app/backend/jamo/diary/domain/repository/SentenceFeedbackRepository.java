package app.backend.jamo.diary.domain.repository;

import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedback;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedbackId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SentenceFeedback Aggregate Repository port.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §1 / §4 / §13 / §14.
 *
 * <p>구현체는 Infrastructure layer 의 {@code SentenceFeedbackRepositoryImpl} —
 * JpaEntity ↔ Domain Mapper 경유.
 */
public interface SentenceFeedbackRepository {

    /** 신규 / 갱신 모두 동일 메서드 (UPSERT) — Aggregate ID 가 영속 키. */
    void save(SentenceFeedback feedback);

    Optional<SentenceFeedback> findById(SentenceFeedbackId id);

    /**
     * 본인 소유 검증을 포함한 조회 — accept / reject 호출 시 사용 (404 IDOR).
     *
     * <p>박제 §4: 다른 사용자 소유 → 404 통일 (403 미사용).
     */
    Optional<SentenceFeedback> findByIdAndUserId(SentenceFeedbackId id, UUID userId);

    /**
     * 일기 삭제 cascade — 박제 §13. {@code diaryId == NULL} row 는 영향 받지 않음 (작성 전 미리보기 흐름,
     * §5). hard-delete.
     *
     * @return 삭제된 row 수 (관측 / 로깅 용도 — 멱등 호출 시 0 가능)
     */
    int deleteAllByDiaryId(UUID diaryId);

    /**
     * 회원 탈퇴 cascade — 박제 §14. 사용자 소유 모든 sentence_feedback row hard-delete (final 상태 무관).
     *
     * <p>호출자: {@code UserWithdrawalRequestedListener} — Saga 시작 이벤트
     * ({@link app.backend.jamo.contracts.event.identity.UserWithdrawalRequested}) 구독 후 실행.
     * 처리 완료 시 {@link app.backend.jamo.contracts.event.identity.UserDataPurged}
     * (sourceService="diary") 회신 발행.
     *
     * @return 삭제된 row 수 (멱등 호출 시 0 가능)
     */
    int deleteAllByUserId(UUID userId);

    /**
     * EXPIRED 전이 배치용 (D-a-5-impl-batch §3) — SUGGESTED 상태이면서 {@code expiresAt < cutoff} 인 row id
     * 목록을 chunk 단위로 조회. 호출자가 단건 트랜잭션으로 load + Aggregate.expire(clock) + save 진행.
     *
     * <p>구현체는 다중 인스턴스 안전을 위해 {@code FOR UPDATE SKIP LOCKED} 적용 (OutboxPoller 정합).
     *
     * @param cutoff 기준 시각 — {@code expiresAt < cutoff} 인 row 만
     * @param limit  chunk 크기 (default 100)
     * @return 후보 Aggregate ID 목록 (size <= limit)
     */
    List<SentenceFeedbackId> findExpirableSuggestedBefore(Instant cutoff, int limit);

    /**
     * 90일 보존 cleanup 용 (§14) — final 상태 (ACCEPTED/REJECTED/EXPIRED/FAILED) 이면서
     * {@code decidedAt < cutoff} 인 row id 목록 chunk 조회.
     *
     * @return 후보 Aggregate ID 목록 (size <= limit)
     */
    List<SentenceFeedbackId> findFinalOlderThan(Instant cutoff, int limit);

    /**
     * Cleanup batch hard-delete — 위 두 조회 메서드 중 final cleanup 에서 사용.
     *
     * @return 실제 삭제된 row 수
     */
    int deleteByIds(List<SentenceFeedbackId> ids);
}
