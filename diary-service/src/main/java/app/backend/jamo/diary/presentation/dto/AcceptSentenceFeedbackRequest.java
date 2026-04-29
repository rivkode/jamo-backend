package app.backend.jamo.diary.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * POST /api/v1/diaries/sentence-feedback/{feedbackId}/accept Request body.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §1 (suggestionId UUID FIX).
 *
 * @param suggestionId 채택할 제안의 식별자 (UUID 문자열)
 */
public record AcceptSentenceFeedbackRequest(
    @NotBlank
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        message = "suggestionId must be UUID")
    String suggestionId
) {
}
