package app.backend.jamo.diary.infrastructure.batch;

import app.backend.jamo.diary.application.service.sentencefeedback.CleanupSentenceFeedbackService;
import app.backend.jamo.diary.application.service.sentencefeedback.ExpireSentenceFeedbackService;
import app.backend.jamo.diary.infrastructure.config.SentenceFeedbackBatchProperties;
import app.backend.jamo.diary.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import app.backend.jamo.diary.infrastructure.persistence.repository.SpringDataProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4 batch 컴포넌트 단위 테스트 (test-reviewer H1) — properties 전달 + service / repository 호출 +
 * 예외 swallow (`@Scheduled` fire-and-forget 안전성).
 *
 * <p>Spring 컨텍스트 X — Mock 만으로 충분 (cron / fixedDelay 자체는 Spring 검증 영역).
 */
class SentenceFeedbackBatchTest {

    private static final Instant NOW = Instant.parse("2026-04-29T10:00:00Z");

    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
    private SentenceFeedbackBatchProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SentenceFeedbackBatchProperties(
            Duration.ofMinutes(5), 100, 90, 30, 7
        );
    }

    // ============================================================
    // SentenceFeedbackExpireBatch
    // ============================================================

    @Test
    void expireBatch_passes_chunkSize_from_properties_and_logs_only_when_candidates_exist() {
        ExpireSentenceFeedbackService service = mock(ExpireSentenceFeedbackService.class);
        when(service.run(100)).thenReturn(new ExpireSentenceFeedbackService.Result(0, 0, 0));
        SentenceFeedbackExpireBatch batch = new SentenceFeedbackExpireBatch(service, properties);

        batch.run();

        verify(service).run(100);
    }

    @Test
    void expireBatch_swallows_service_exception_does_not_propagate_to_scheduler() {
        // batch fire-and-forget — 예외 propagate 시 다음 사이클 차단 위험. try/catch 보장 검증.
        ExpireSentenceFeedbackService service = mock(ExpireSentenceFeedbackService.class);
        when(service.run(anyInt())).thenThrow(new RuntimeException("DB down"));
        SentenceFeedbackExpireBatch batch = new SentenceFeedbackExpireBatch(service, properties);

        assertThatNoException().isThrownBy(batch::run);
    }

    // ============================================================
    // SentenceFeedbackCleanupBatch
    // ============================================================

    @Test
    void cleanupBatch_passes_retentionDays_and_chunkSize_from_properties() {
        CleanupSentenceFeedbackService service = mock(CleanupSentenceFeedbackService.class);
        when(service.run(90, 100)).thenReturn(0);
        SentenceFeedbackCleanupBatch batch = new SentenceFeedbackCleanupBatch(service, properties);

        batch.run();

        verify(service).run(90, 100);
    }

    @Test
    void cleanupBatch_swallows_service_exception() {
        CleanupSentenceFeedbackService service = mock(CleanupSentenceFeedbackService.class);
        when(service.run(anyInt(), anyInt())).thenThrow(new RuntimeException("DB down"));
        SentenceFeedbackCleanupBatch batch = new SentenceFeedbackCleanupBatch(service, properties);

        assertThatNoException().isThrownBy(batch::run);
    }

    // ============================================================
    // ProcessedEventCleanupBatch
    // ============================================================

    @Test
    void processedEventBatch_uses_clock_minus_retentionDays_as_cutoff() {
        SpringDataProcessedEventRepository repo = mock(SpringDataProcessedEventRepository.class);
        when(repo.deleteProcessedBefore(any())).thenReturn(0);
        ProcessedEventCleanupBatch batch = new ProcessedEventCleanupBatch(repo, properties, fixedClock);

        batch.run();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(repo).deleteProcessedBefore(captor.capture());
        assertThat(captor.getValue()).isEqualTo(NOW.minus(Duration.ofDays(30)));
    }

    @Test
    void processedEventBatch_swallows_repository_exception() {
        SpringDataProcessedEventRepository repo = mock(SpringDataProcessedEventRepository.class);
        when(repo.deleteProcessedBefore(any())).thenThrow(new RuntimeException("DB down"));
        ProcessedEventCleanupBatch batch = new ProcessedEventCleanupBatch(repo, properties, fixedClock);

        assertThatNoException().isThrownBy(batch::run);
    }

    // ============================================================
    // PublishedOutboxCleanupBatch
    // ============================================================

    @Test
    void outboxBatch_uses_clock_minus_retentionDays_as_cutoff() {
        SpringDataOutboxEventRepository repo = mock(SpringDataOutboxEventRepository.class);
        when(repo.deletePublishedBefore(any())).thenReturn(0);
        PublishedOutboxCleanupBatch batch = new PublishedOutboxCleanupBatch(repo, properties, fixedClock);

        batch.run();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(repo).deletePublishedBefore(captor.capture());
        assertThat(captor.getValue()).isEqualTo(NOW.minus(Duration.ofDays(7)));
    }

    @Test
    void outboxBatch_swallows_repository_exception() {
        SpringDataOutboxEventRepository repo = mock(SpringDataOutboxEventRepository.class);
        when(repo.deletePublishedBefore(any())).thenThrow(new RuntimeException("DB down"));
        PublishedOutboxCleanupBatch batch = new PublishedOutboxCleanupBatch(repo, properties, fixedClock);

        assertThatNoException().isThrownBy(batch::run);
    }
}
