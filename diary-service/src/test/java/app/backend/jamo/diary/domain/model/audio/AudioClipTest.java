package app.backend.jamo.diary.domain.model.audio;

import app.backend.jamo.diary.domain.exception.InvalidAudioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioClipTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @Test
    void wav_upload_creates_clip_with_uuid_dot_wav_name() {
        AudioClip clip = AudioClip.create(OWNER, "audio/wav", 1024, NOW);

        assertThat(clip.contentType()).isEqualTo("audio/wav");
        assertThat(clip.sizeBytes()).isEqualTo(1024);
        assertThat(clip.ownerUserId()).isEqualTo(OWNER);
        assertThat(clip.storedName()).isEqualTo(clip.id().asString() + ".wav");
    }

    @ParameterizedTest
    @CsvSource({
        "audio/wav,wav", "audio/x-wav,wav", "audio/wave,wav", "audio/mpeg,mp3",
        "audio/mp4,m4a", "audio/aac,aac", "audio/webm,webm", "audio/ogg,ogg"
    })
    void content_type_maps_to_expected_extension(String contentType, String ext) {
        AudioClip clip = AudioClip.create(OWNER, contentType, 10, NOW);
        assertThat(clip.storedName()).endsWith("." + ext);
    }

    @Test
    void content_type_with_charset_param_is_normalized() {
        AudioClip clip = AudioClip.create(OWNER, "audio/webm; codecs=opus", 10, NOW);
        assertThat(clip.contentType()).isEqualTo("audio/webm");
        assertThat(clip.storedName()).endsWith(".webm");
    }

    @Test
    void unsupported_content_type_rejected() {
        assertThatThrownBy(() -> AudioClip.create(OWNER, "application/zip", 10, NOW))
            .isInstanceOf(InvalidAudioException.class)
            .hasMessageContaining("unsupported");
    }

    @Test
    void blank_content_type_rejected() {
        assertThatThrownBy(() -> AudioClip.create(OWNER, "  ", 10, NOW))
            .isInstanceOf(InvalidAudioException.class);
    }

    @Test
    void empty_content_rejected() {
        assertThatThrownBy(() -> AudioClip.create(OWNER, "audio/wav", 0, NOW))
            .isInstanceOf(InvalidAudioException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void over_max_size_rejected() {
        assertThatThrownBy(() -> AudioClip.create(OWNER, "audio/wav", AudioClip.MAX_SIZE_BYTES + 1, NOW))
            .isInstanceOf(InvalidAudioException.class)
            .hasMessageContaining("max size");
    }

    @Test
    void exactly_max_size_accepted() {
        AudioClip clip = AudioClip.create(OWNER, "audio/wav", AudioClip.MAX_SIZE_BYTES, NOW);
        assertThat(clip.sizeBytes()).isEqualTo(AudioClip.MAX_SIZE_BYTES);
    }
}
