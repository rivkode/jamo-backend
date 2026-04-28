package app.backend.jamo.diary.domain.exception;

/**
 * Suggestion VO 의 invariant 위반 — text/reason blank 또는 길이 초과 / confidence 범위 외.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §8 (Suggestion 4 필드 invariant)
 * + ddd-architect Q2/Q3 NEEDS CHANGES (text 200 cp 상한 + reason blank 차단).
 *
 * <p>chat-service 가 발행한 데이터의 도메인 게이트 — chat-service invariant 위반 시점에 발견.
 *
 * <p>Presentation 매핑: HTTP 502 (Bad Gateway) — chat-service 의 응답이 본 도메인 invariant 와 어긋난 경우.
 * 사용자 입력 invariant ({@link InvalidSentenceTextException}) 의 400 과 의미 분리.
 */
public class InvalidSuggestionException extends IllegalArgumentException {
    public InvalidSuggestionException(String message) {
        super(message);
    }
}
