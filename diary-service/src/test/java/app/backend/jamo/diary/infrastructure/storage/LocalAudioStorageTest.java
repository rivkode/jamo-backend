package app.backend.jamo.diary.infrastructure.storage;

import app.backend.jamo.diary.infrastructure.config.AudioStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAudioStorageTest {

    @TempDir
    Path tempDir;

    private LocalAudioStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalAudioStorage(new AudioStorageProperties(tempDir.toString()));
    }

    @Test
    void store_then_load_roundtrip() {
        byte[] content = {1, 2, 3, 4};
        storage.store("abc.wav", content);

        Optional<byte[]> loaded = storage.load("abc.wav");
        assertThat(loaded).isPresent();
        assertThat(loaded.get()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void load_missing_returns_empty() {
        assertThat(storage.load("nope.wav")).isEmpty();
    }

    @Test
    void store_existing_name_fails() {
        storage.store("dup.wav", new byte[]{1});
        assertThatThrownBy(() -> storage.store("dup.wav", new byte[]{2}))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void path_traversal_name_rejected_on_store() {
        assertThatThrownBy(() -> storage.store("../escape.wav", new byte[]{1}))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void path_traversal_name_rejected_on_load() {
        assertThatThrownBy(() -> storage.load("../../etc/passwd"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slash_in_name_rejected() {
        assertThatThrownBy(() -> storage.load("sub/dir.wav"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
