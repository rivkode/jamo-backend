package app.backend.jamo.chat.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * STT Command — presentation 이 multipart 에서 추출.
 *
 * @param userId   인증 사용자 (응답 transcribeInfo.userId)
 * @param audio    음성 바이너리
 * @param format   포맷 (wav/mp3/... — 파일 확장자/content-type 에서 유도)
 * @param language BCP-47 (null = ai-service 자동 감지)
 */
public record TranscribeCommand(UUID userId, byte[] audio, String format, String language) {

    public TranscribeCommand {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(audio, "audio");
    }
}
