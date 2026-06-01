package app.backend.jamo.diary.presentation.dto.diarychat;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/v1/diary-chatrooms/{roomId}/messages Request (API_SPEC E2.9).
 *
 * @param text     본문 (필수 1..1000 — 도메인 MessageText 강제). STT 는 클라가 처리해 text 로 전송.
 * @param audioUrl 첨부 녹음 URL (optional, E.5 업로드 결과). null/생략 가능.
 */
public record SendMessageRequest(
    @NotBlank(message = "text is required")
    String text,
    String audioUrl
) {
}
