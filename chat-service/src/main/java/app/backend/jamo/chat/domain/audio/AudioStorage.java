package app.backend.jamo.chat.domain.audio;

import java.util.Optional;

/**
 * TTS 합성 음성 바이너리 저장/조회 port (구현은 infrastructure — 로컬 파일시스템, 후속 S3).
 * {@code storedName} 단일 키. 구현체가 path traversal 차단.
 */
public interface AudioStorage {

    void store(String storedName, byte[] content);

    Optional<byte[]> load(String storedName);
}
