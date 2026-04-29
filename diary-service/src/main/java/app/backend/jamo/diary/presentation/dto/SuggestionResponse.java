package app.backend.jamo.diary.presentation.dto;

import app.backend.jamo.diary.application.dto.sentencefeedback.SuggestionView;

/**
 * 응답의 suggestion 단건 — 박제 응답 schema 의 4 필드 (suggestionId/text/reason/confidence).
 */
public record SuggestionResponse(
    String suggestionId,
    String text,
    String reason,
    double confidence
) {
    public static SuggestionResponse from(SuggestionView view) {
        return new SuggestionResponse(
            view.suggestionId().toString(),
            view.text(),
            view.reason(),
            view.confidence()
        );
    }
}
