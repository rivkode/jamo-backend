package app.backend.jamo.chat.presentation.dto;

import java.util.UUID;

/**
 * POST /api/v1/chat/speech 응답 (API_SPEC 부록 E.3): {@code { audioSpeechInfo: { userId, speechText, filePath } }}.
 * 프론트가 filePath({@code /app/audio-storage/{name}}) → {@code {origin}/audio/{name}} 변환해 재생. audioUrl 도 함께 제공.
 */
public record SpeechResponse(AudioSpeechInfo audioSpeechInfo) {

    public record AudioSpeechInfo(String userId, String speechText, String filePath, String audioUrl) {
    }

    public static SpeechResponse of(UUID userId, String speechText, String filePath, String audioUrl) {
        return new SpeechResponse(new AudioSpeechInfo(userId.toString(), speechText, filePath, audioUrl));
    }
}
