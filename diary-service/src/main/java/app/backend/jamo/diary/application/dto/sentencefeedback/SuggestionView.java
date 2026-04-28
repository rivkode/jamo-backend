package app.backend.jamo.diary.application.dto.sentencefeedback;

import app.backend.jamo.diary.domain.model.sentencefeedback.Suggestion;

import java.util.UUID;

/**
 * 응답용 Suggestion view (Application layer DTO).
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §8 — SentenceSuggestion 4 필드.
 *
 * <p>도메인 {@link Suggestion} VO 를 외부 응답 형태로 평탄화 — Presentation layer 로 흘러가는 안전한 view.
 * 도메인 VO 가 직접 노출되면 외부 변경 시 도메인 invariant 영향 위험 — DTO 계층 분리.
 *
 * @param suggestionId UUID 문자열
 * @param text         제안 문장 본문
 * @param reason       제안 사유
 * @param confidence   0.0 ~ 1.0
 */
public record SuggestionView(
    UUID suggestionId,
    String text,
    String reason,
    double confidence
) {
    public static SuggestionView from(Suggestion suggestion) {
        return new SuggestionView(
            suggestion.id().value(),
            suggestion.text(),
            suggestion.reason(),
            suggestion.confidence()
        );
    }
}
