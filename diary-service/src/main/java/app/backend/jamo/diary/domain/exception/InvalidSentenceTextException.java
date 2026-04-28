package app.backend.jamo.diary.domain.exception;

/**
 * SentenceText VO 의 invariant 위반 — 길이 (1..50 code points) 또는 blank.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §9 (50 code points 산정 + 금칙어 도메인 invariant).
 *
 * <p>Presentation 매핑: HTTP 400 (Bad Request, `SENTENCE_LENGTH_EXCEEDED` 또는 동등).
 */
public class InvalidSentenceTextException extends RuntimeException {
    public InvalidSentenceTextException(String message) {
        super(message);
    }
}
