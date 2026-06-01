package app.backend.jamo.diary.application.service.audio;

import app.backend.jamo.diary.application.dto.audio.AudioUploadResult;
import app.backend.jamo.diary.application.dto.audio.UploadAudioCommand;
import app.backend.jamo.diary.domain.exception.InvalidAudioException;
import app.backend.jamo.diary.domain.model.audio.AudioClip;
import app.backend.jamo.diary.domain.model.audio.AudioStorage;
import app.backend.jamo.diary.domain.repository.AudioClipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadAudioServiceTest {

    private static final UUID OWNER = UUID.randomUUID();

    private AudioClipRepository repository;
    private AudioStorage storage;
    private UploadAudioService service;

    @BeforeEach
    void setUp() {
        repository = mock(AudioClipRepository.class);
        storage = mock(AudioStorage.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);
        service = new UploadAudioService(repository, storage, clock);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void stores_binary_before_metadata_and_returns_result() {
        byte[] content = {1, 2, 3};
        AudioUploadResult result = service.upload(new UploadAudioCommand(OWNER, content, "audio/wav"));

        assertThat(result.storedName()).endsWith(".wav");
        assertThat(result.contentType()).isEqualTo("audio/wav");
        assertThat(result.sizeBytes()).isEqualTo(3);

        // 바이너리 저장이 메타 영속보다 먼저 (서빙 시 메타는 있는데 파일 없는 상태 회피)
        var order = inOrder(storage, repository);
        order.verify(storage).store(any(), any());
        order.verify(repository).save(any());

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(storage).store(nameCaptor.capture(), any());
        assertThat(nameCaptor.getValue()).isEqualTo(result.storedName());
    }

    @Test
    void unsupported_type_rejected_before_any_storage() {
        assertThatThrownBy(() -> service.upload(new UploadAudioCommand(OWNER, new byte[]{1}, "image/png")))
            .isInstanceOf(InvalidAudioException.class);

        verify(storage, never()).store(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void markup_content_rejected_before_storage() {
        byte[] html = "<html><script>x</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.upload(new UploadAudioCommand(OWNER, html, "audio/wav")))
            .isInstanceOf(app.backend.jamo.diary.domain.exception.InvalidAudioException.class);

        verify(storage, never()).store(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void metadata_save_failure_leaves_binary_stored_and_propagates() {
        // docstring 결정: 바이너리 먼저 저장 → 메타 save 실패해도 보상(파일 삭제) 하지 않음 (고아 무해, M1 후속 정리).
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.upload(new UploadAudioCommand(OWNER, new byte[]{1, 2}, "audio/wav")))
            .isInstanceOf(RuntimeException.class);

        verify(storage).store(any(), any());  // 바이너리는 이미 저장됨 — 의도된 동작
    }

    @Test
    void clip_owner_matches_command() {
        ArgumentCaptor<AudioClip> captor = ArgumentCaptor.forClass(AudioClip.class);
        service.upload(new UploadAudioCommand(OWNER, new byte[]{1}, "audio/mpeg"));
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().ownerUserId()).isEqualTo(OWNER);
    }
}
