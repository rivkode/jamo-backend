package app.backend.jamo.diary.domain.exception;

/**
 * {@code accept(suggestionId)} 호출 시 suggestionId 가 Aggregate 의 suggestions 안에 존재하지 않을 때.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §2, §4 (`SENTENCE_FEEDBACK_UNKNOWN_SUGGESTION`).
 * 상태 전이 (SUGGESTED → ACCEPTED) 자체는 정상 — 입력 데이터가 잘못된 것이므로 InvalidTransition (409) 아님.
 *
 * <p>Presentation 매핑: HTTP 400 (Bad Request).
 */
public class SentenceFeedbackUnknownSuggestionException extends RuntimeException {
    public SentenceFeedbackUnknownSuggestionException(String message) {
        super(message);
    }
}
