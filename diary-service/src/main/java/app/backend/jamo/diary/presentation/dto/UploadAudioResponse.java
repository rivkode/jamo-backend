package app.backend.jamo.diary.presentation.dto;

import app.backend.jamo.diary.application.dto.audio.AudioUploadResult;

/**
 * 음성 업로드 응답.
 *
 * <p>{@code audioUrl} 은 즉시 재생 가능한 절대 URL ({@code {origin}/audio/{name}}), {@code filePath} 는
 * API_SPEC 부록 E 의 레거시 규약({@code /app/audio-storage/{name}}) 호환 — 프론트가 둘 중 편한 쪽 사용.
 */
public record UploadAudioResponse(
    String audioUrl,
    String filePath,
    String fileName,
    String contentType,
    long sizeBytes
) {
    public static UploadAudioResponse of(AudioUploadResult result, String audioUrl, String filePath) {
        return new UploadAudioResponse(
            audioUrl, filePath, result.storedName(), result.contentType(), result.sizeBytes());
    }
}
