package app.backend.jamo.chat.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/chat/speech Request (API_SPEC 부록 E.3).
 *
 * @param speechText 합성할 텍스트 (필수, ≤4096 — OpenAI tts-1 한도. 비용/DoS 1차 방어)
 * @param chatId     레거시 필드 — 본 게이트웨이는 미사용 (stateless). null 허용
 */
public record SpeechRequest(
    @NotBlank(message = "speechText is required")
    @Size(max = 4096, message = "speechText too long (max 4096)")
    String speechText,
    Integer chatId
) {
}
