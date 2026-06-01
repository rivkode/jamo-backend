package app.backend.jamo.diary.presentation.dto.diarychat;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/v1/diary-chatrooms Request (API_SPEC E2.1).
 *
 * @param diaryId            대상 일기 UUID (문자열 — Controller 가 파싱, 형식 오류 400)
 * @param aiAssistantEnabled null/생략 시 default true (Controller 책임)
 */
public record CreateChatRoomRequest(
    @NotBlank(message = "diaryId is required")
    String diaryId,
    Boolean aiAssistantEnabled
) {
}
