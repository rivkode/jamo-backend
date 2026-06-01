package app.backend.jamo.diary.domain.model.audio;

import java.util.Optional;

/**
 * 음성 바이너리 저장/조회 port (구현은 infrastructure — 로컬 파일시스템, 후속 S3).
 *
 * <p>도메인은 저장 위치/방식을 모른다 — {@code storedName} 단일 키로만 다룬다. 구현체는 path traversal 등
 * 위협을 차단할 책임.
 */
public interface AudioStorage {

    /**
     * 본문을 {@code storedName} 으로 저장. 이미 존재하면 덮어쓰지 않고 예외 (UUID 충돌은 사실상 0).
     */
    void store(String storedName, byte[] content);

    /**
     * 저장된 본문 조회. 메타는 존재하나 파일이 없을 수 있어 {@link Optional}.
     */
    Optional<byte[]> load(String storedName);
}
