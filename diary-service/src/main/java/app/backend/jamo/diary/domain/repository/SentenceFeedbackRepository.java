package app.backend.jamo.diary.domain.repository;

import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedback;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedbackId;

import java.util.Optional;
import java.util.UUID;

/**
 * SentenceFeedback Aggregate Repository port.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §1 / §4.
 *
 * <p>구현체는 Infrastructure layer (D-a-5-impl-infra) 의 {@code SentenceFeedbackRepositoryImpl} —
 * JpaEntity ↔ Domain Mapper 경유.
 *
 * <p>후속 슬라이스에서 추가될 메서드:
 * <ul>
 *   <li>{@code findExpirableSuggestedBefore(Instant cutoff, int limit)} — 배치 EXPIRED 전이 (D-a-5-impl-batch)</li>
 *   <li>{@code deleteAllByUserId(UUID)} — UserDataPurged GDPR (D-a-5-impl-infra)</li>
 *   <li>{@code deleteAllByDiaryId(UUID)} — DiaryDeleted Saga cascade (D-a-5-impl-infra)</li>
 * </ul>
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
}
