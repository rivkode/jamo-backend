package app.backend.jamo.diary.infrastructure.batch;

import app.backend.jamo.diary.infrastructure.config.SentenceFeedbackBatchProperties;
import app.backend.jamo.diary.infrastructure.persistence.repository.SpringDataProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * ProcessedEvent retention cleanup — code-reviewer L3 (PR #71) 박제 후속.
 *
 * <p>Default 30일 — Kafka 의 max retention 보다 충분히 길어 재 delivery 가능성 거의 없음. 큰 운영 데이터
 * 누적 시 disk pressure 회피.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProcessedEventCleanupBatch {

    private final SpringDataProcessedEventRepository repository;
    private final SentenceFeedbackBatchProperties properties;
    private final Clock clock;

    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Seoul")
    @Transactional
    public void run() {
        try {
            Instant cutoff = Instant.now(clock).minus(Duration.ofDays(properties.processedEventRetentionDays()));
            int deleted = repository.deleteProcessedBefore(cutoff);
            if (deleted > 0) {
                log.info("processed_event retention cleanup deleted={} retentionDays={}",
                    deleted, properties.processedEventRetentionDays());
            }
        } catch (Exception ex) {
            log.error("processed_event retention cleanup failed", ex);
        }
    }
}
