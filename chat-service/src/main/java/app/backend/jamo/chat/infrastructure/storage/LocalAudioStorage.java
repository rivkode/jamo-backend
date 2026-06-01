package app.backend.jamo.chat.infrastructure.storage;

import app.backend.jamo.chat.domain.audio.AudioStorage;
import app.backend.jamo.chat.infrastructure.config.AudioStorageProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 로컬 파일시스템 {@link AudioStorage} 구현 (dev — 운영은 후속 S3). diary-service LocalAudioStorage 정합.
 *
 * <p>path traversal 방어: storedName 은 단순 파일명 — 구분자/상위참조 거부 + normalize 후 baseDir 내부 재확인.
 */
@Component
public class LocalAudioStorage implements AudioStorage {

    private final Path baseDir;

    public LocalAudioStorage(AudioStorageProperties properties) {
        this.baseDir = Path.of(properties.storageDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create audio storage dir: " + baseDir, e);
        }
    }

    @Override
    public void store(String storedName, byte[] content) {
        Path target = resolveSafe(storedName);
        if (Files.exists(target)) {
            throw new IllegalStateException("audio already exists: " + storedName);
        }
        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to store audio: " + storedName, e);
        }
    }

    @Override
    public Optional<byte[]> load(String storedName) {
        Path target = resolveSafe(storedName);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(target));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read audio: " + storedName, e);
        }
    }

    private Path resolveSafe(String storedName) {
        if (storedName == null || storedName.isBlank()
            || storedName.contains("/") || storedName.contains("\\") || storedName.contains("..")) {
            throw new IllegalArgumentException("invalid audio name: " + storedName);
        }
        Path resolved = baseDir.resolve(storedName).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("audio name escapes storage dir: " + storedName);
        }
        return resolved;
    }
}
