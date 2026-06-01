package app.backend.jamo.diary.presentation.dto.diarychat;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/v1/diary-chatrooms/{roomId}/ai-toggle Request (API_SPEC E2.6).
 */
public record ToggleAiAssistantRequest(
    @NotNull(message = "enabled is required")
    Boolean enabled
) {
}
