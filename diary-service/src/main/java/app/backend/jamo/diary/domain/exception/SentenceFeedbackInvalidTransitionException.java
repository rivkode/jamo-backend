package app.backend.jamo.diary.domain.exception;

/**
 * SentenceFeedback 의 라이프사이클 전이 invariant 위반 시 던져진다.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §2, §4.
 *
 * <p>케이스:
 * <ul>
 *   <li>final 상태 (ACCEPTED / REJECTED / EXPIRED / FAILED) 에서 추가 전이 시도</li>
 *   <li>{@code expire(clock)} 호출 시 {@code clock.instant() < expiresAt} (TTL 미도래) — ddd-architect Q8 NEEDS CHANGES</li>
 *   <li>잘못된 시작 상태에서의 전이 (예: REQUESTED 에서 accept)</li>
 * </ul>
 *
 * <p>Presentation 매핑: HTTP 409 (Conflict — 정상 권한 + 비정상 상태, IDOR 위험 없음).
 */
public class SentenceFeedbackInvalidTransitionException extends RuntimeException {
    public SentenceFeedbackInvalidTransitionException(String message) {
        super(message);
    }
}
