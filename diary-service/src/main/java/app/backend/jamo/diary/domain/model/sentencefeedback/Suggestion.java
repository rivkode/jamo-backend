package app.backend.jamo.diary.domain.model.sentencefeedback;

import app.backend.jamo.diary.domain.exception.InvalidSuggestionException;

import java.util.Objects;

/**
 * AI 가 반환한 문장 대안 제안 (VO).
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §8 응답 schema 의 SentenceSuggestion 4 필드.
 *
 * <p><b>라이프사이클이 SentenceFeedback Aggregate 에 종속</b> — Suggestion 은 단독 수정 메서드 없음.
 * AR 외부에서 참조하지 않으므로 식별자 ({@link SuggestionId}) 가 있어도 VO (Evans DDD).
 *
 * <p>invariant (ddd-architect Q2 / Q3 NEEDS CHANGES 반영):
 * <ul>
 *   <li>{@code text}: blank 차단 + 1..200 code points (chat-service 결정 시 동기화 박제)</li>
 *   <li>{@code reason}: <b>blank 차단</b> — AI 가 reason 비워 보내는 것은 chat-service invariant 위반</li>
 *   <li>{@code confidence}: 0.0 ~ 1.0 + finite (NaN / Infinity 차단)</li>
 * </ul>
 *
 * <p>본 도메인은 chat-service 가 발행한 데이터의 게이트 — invariant 위반은 IllegalArgumentException.
 *
 * @param id Suggestion 식별자 — AR 내부 매칭 ({@code accept(suggestionId)}) 용
 * @param text 제안 문장 본문
 * @param reason 제안 사유 (UI 노출 텍스트)
 * @param confidence 0.0 ~ 1.0
 */
public record Suggestion(SuggestionId id, String text, String reason, double confidence) {

    public static final int TEXT_MAX_CODE_POINTS = 200;
    public static final int REASON_MAX_CODE_POINTS = 500;

    public Suggestion {
        Objects.requireNonNull(id, "id");
        requireNonBlank(text, "text");
        requireMaxCodePoints(text, TEXT_MAX_CODE_POINTS, "text");
        requireNonBlank(reason, "reason");
        requireMaxCodePoints(reason, REASON_MAX_CODE_POINTS, "reason");
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new InvalidSuggestionException(
                "confidence must be in [0.0, 1.0] and finite, got " + confidence
            );
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isEmpty() || value.codePoints().allMatch(Character::isWhitespace)) {
            throw new InvalidSuggestionException(name + " must not be blank");
        }
    }

    private static void requireMaxCodePoints(String value, int max, String name) {
        int cp = value.codePointCount(0, value.length());
        if (cp > max) {
            throw new InvalidSuggestionException(
                name + " length out of range: max " + max + " code points, got " + cp
            );
        }
    }
}
