package app.backend.jamo.diary.domain.exception;

/**
 * 사용자별 분당 / 일일 호출 한도 초과 (HTTP 429).
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §11 (분 10회 / 일 50회).
 */
public class SentenceFeedbackRateLimitedException extends RuntimeException {

    public SentenceFeedbackRateLimitedException(String message) {
        super(message);
    }
}
