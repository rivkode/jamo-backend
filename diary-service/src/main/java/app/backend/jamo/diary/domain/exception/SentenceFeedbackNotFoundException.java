package app.backend.jamo.diary.domain.exception;

/**
 * SentenceFeedback Aggregate 가 존재하지 않거나, 본인 소유가 아닐 때 (404 IDOR) 던져진다.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §4 — 다른 사용자 소유 / 없는 feedbackId
 * 모두 404 통일 (comment / diary / diarychat 정합).
 *
 * <p>Presentation 매핑: HTTP 404.
 */
public class SentenceFeedbackNotFoundException extends RuntimeException {
    public SentenceFeedbackNotFoundException(String message) {
        super(message);
    }
}
