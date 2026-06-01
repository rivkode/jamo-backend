package app.backend.jamo.chat.presentation.dto;

import java.util.UUID;

/**
 * POST /api/v1/chat/transcribe 응답 (API_SPEC 부록 E.3): {@code { transcribeInfo: { userId, text } }}.
 * 프론트 TranscribeResponse.text 가 transcribeInfo.text 를 읽음.
 */
public record TranscribeResponse(TranscribeInfo transcribeInfo) {

    public record TranscribeInfo(String userId, String text) {
    }

    public static TranscribeResponse of(UUID userId, String text) {
        return new TranscribeResponse(new TranscribeInfo(userId.toString(), text));
    }
}
