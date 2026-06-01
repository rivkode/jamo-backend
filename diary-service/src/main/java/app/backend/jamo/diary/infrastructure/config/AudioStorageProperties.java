package app.backend.jamo.diary.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 음성 바이너리 로컬 저장 위치 설정.
 *
 * <p>dev/local 기본 {@code /app/audio-storage} (API_SPEC 부록 E 의 filePath 규약 정합 — 컨테이너 내부 경로).
 * 운영은 후속 S3 전환. env {@code AUDIO_STORAGE_DIR} override.
 */
@ConfigurationProperties(prefix = "jamo.audio")
public record AudioStorageProperties(String storageDir) {

    public AudioStorageProperties {
        if (storageDir == null || storageDir.isBlank()) {
            throw new IllegalArgumentException("jamo.audio.storage-dir must not be blank");
        }
    }
}
