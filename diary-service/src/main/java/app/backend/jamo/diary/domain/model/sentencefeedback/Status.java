package app.backend.jamo.diary.domain.model.sentencefeedback;

/**
 * SentenceFeedback Aggregate 의 라이프사이클 상태.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §2.
 *
 * <pre>
 * REQUESTED ─(markSuggested)─▶ SUGGESTED ─┬─(accept)─▶ ACCEPTED  (final)
 *                                         ├─(reject)─▶ REJECTED  (final)
 *                                         └─(expire)─▶ EXPIRED   (final)
 *
 * REQUESTED ─(markFailed)──▶ FAILED  (final)
 * </pre>
 *
 * <p>final 상태 (ACCEPTED / REJECTED / EXPIRED / FAILED) 에서의 상태 전이는 invariant 위반 —
 * Aggregate 가 {@code SentenceFeedbackInvalidTransitionException} 을 던진다.
 */
public enum Status {
    REQUESTED,
    SUGGESTED,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    FAILED;

    /** ACCEPTED / REJECTED / EXPIRED / FAILED 는 final 상태. */
    public boolean isFinal() {
        return this == ACCEPTED || this == REJECTED || this == EXPIRED || this == FAILED;
    }
}
