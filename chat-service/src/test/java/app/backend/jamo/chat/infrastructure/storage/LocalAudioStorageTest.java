package app.backend.jamo.chat.infrastructure.storage;

import app.backend.jamo.chat.infrastructure.config.AudioStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

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
        storage.store("abc.mp3", new byte[]{1, 2, 3});
        assertThat(storage.load("abc.mp3")).isPresent().get().isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    void load_missing_returns_empty() {
        assertThat(storage.load("nope.mp3")).isEmpty();
    }

    @Test
    void traversal_dotdot_rejected() {
        assertThatThrownBy(() -> storage.load("../../etc/passwd"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slash_in_name_rejected() {
        assertThatThrownBy(() -> storage.store("sub/dir.mp3", new byte[]{1}))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_existing_fails() {
        storage.store("dup.mp3", new byte[]{1});
        assertThatThrownBy(() -> storage.store("dup.mp3", new byte[]{2}))
            .isInstanceOf(IllegalStateException.class);
    }
}
