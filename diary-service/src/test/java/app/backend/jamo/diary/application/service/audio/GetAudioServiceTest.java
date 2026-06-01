package app.backend.jamo.diary.application.service.audio;

import app.backend.jamo.diary.application.dto.audio.AudioContent;
import app.backend.jamo.diary.domain.exception.AudioClipNotFoundException;
import app.backend.jamo.diary.domain.model.audio.AudioClip;
import app.backend.jamo.diary.domain.model.audio.AudioStorage;
import app.backend.jamo.diary.domain.repository.AudioClipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetAudioServiceTest {

    private AudioClipRepository repository;
    private AudioStorage storage;
    private GetAudioService service;

    @BeforeEach
    void setUp() {
        repository = mock(AudioClipRepository.class);
        storage = mock(AudioStorage.class);
        service = new GetAudioService(repository, storage);
    }

    private AudioClip clip(String storedName, String contentType) {
        return AudioClip.create(UUID.randomUUID(), contentType, 4, Instant.parse("2026-06-01T10:00:00Z"));
    }

    @Test
    void returns_content_and_type_when_present() {
        AudioClip clip = clip("x.wav", "audio/wav");
        when(repository.findByStoredName(clip.storedName())).thenReturn(Optional.of(clip));
        when(storage.load(clip.storedName())).thenReturn(Optional.of(new byte[]{9, 9}));

        AudioContent content = service.get(clip.storedName());

        assertThat(content.contentType()).isEqualTo("audio/wav");
        assertThat(content.content()).containsExactly(9, 9);
    }

    @Test
    void metadata_absent_throws_not_found() {
        when(repository.findByStoredName("missing.wav")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("missing.wav"))
            .isInstanceOf(AudioClipNotFoundException.class);
    }

    @Test
    void binary_missing_throws_not_found() {
        AudioClip clip = clip("y.mp3", "audio/mpeg");
        when(repository.findByStoredName(clip.storedName())).thenReturn(Optional.of(clip));
        when(storage.load(clip.storedName())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(clip.storedName()))
            .isInstanceOf(AudioClipNotFoundException.class)
            .hasMessageContaining("missing");
    }
}
