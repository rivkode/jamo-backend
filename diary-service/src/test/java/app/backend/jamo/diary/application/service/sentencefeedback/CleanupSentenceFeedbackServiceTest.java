package app.backend.jamo.diary.application.service.sentencefeedback;

import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedbackId;
import app.backend.jamo.diary.domain.repository.SentenceFeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CleanupSentenceFeedbackServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-29T10:00:00Z");

    private SentenceFeedbackRepository repository;
    private CleanupSentenceFeedbackService service;
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        repository = mock(SentenceFeedbackRepository.class);
        service = new CleanupSentenceFeedbackService(repository, clock);
    }

    @Test
    void empty_chunk_returns_zero_no_delete_call() {
        when(repository.findFinalOlderThan(any(), any(Integer.class))).thenReturn(List.of());

        int deleted = service.run(90, 100);

        assertThat(deleted).isZero();
        verify(repository, never()).deleteByIds(any());
    }

    @Test
    void cutoff_is_now_minus_retentionDays() {
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        when(repository.findFinalOlderThan(any(), any(Integer.class))).thenReturn(List.of());

        service.run(90, 100);

        verify(repository).findFinalOlderThan(captor.capture(), any(Integer.class));
        assertThat(captor.getValue()).isEqualTo(NOW.minus(Duration.ofDays(90)));
    }

    @Test
    void deletes_chunk_returns_actual_count() {
        SentenceFeedbackId id1 = SentenceFeedbackId.newId();
        SentenceFeedbackId id2 = SentenceFeedbackId.newId();
        when(repository.findFinalOlderThan(any(), any(Integer.class))).thenReturn(List.of(id1, id2));
        when(repository.deleteByIds(List.of(id1, id2))).thenReturn(2);

        int deleted = service.run(90, 100);

        assertThat(deleted).isEqualTo(2);
        verify(repository).deleteByIds(List.of(id1, id2));
    }

    @Test
    void chunk_size_passed_through() {
        when(repository.findFinalOlderThan(any(), any(Integer.class))).thenReturn(List.of());

        service.run(90, 50);

        verify(repository).findFinalOlderThan(any(), eq(50));
    }
}
