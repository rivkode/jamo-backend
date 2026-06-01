package app.backend.jamo.diary.application.dto.audio;

import java.util.Objects;
import java.util.UUID;

/**
 * 음성 업로드 Command — presentation 이 multipart 에서 추출해 전달.
 *
 * @param ownerUserId 업로더 (인증 사용자)
 * @param content     오디오 바이너리
 * @param contentType MIME type (도메인 화이트리스트 검증 대상)
 */
public record UploadAudioCommand(UUID ownerUserId, byte[] content, String contentType) {

    public UploadAudioCommand {
        Objects.requireNonNull(ownerUserId, "ownerUserId");
        Objects.requireNonNull(content, "content");
    }
}
