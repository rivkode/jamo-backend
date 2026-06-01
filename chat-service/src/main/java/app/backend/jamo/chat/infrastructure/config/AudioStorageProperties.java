package app.backend.jamo.chat.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TTS 합성 음성 로컬 저장 위치 (chat-service). dev 기본 {@code /app/audio-storage} (API_SPEC 부록 E.3
 * filePath 규약). 운영은 후속 S3. env {@code AUDIO_STORAGE_DIR} override.
 */
@ConfigurationProperties(prefix = "jamo.audio")
public record AudioStorageProperties(String storageDir) {

    public AudioStorageProperties {
        if (storageDir == null || storageDir.isBlank()) {
            throw new IllegalArgumentException("jamo.audio.storage-dir must not be blank");
        }
    }
}
