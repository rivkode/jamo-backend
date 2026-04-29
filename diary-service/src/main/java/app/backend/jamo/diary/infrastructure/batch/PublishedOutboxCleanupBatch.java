package app.backend.jamo.diary.infrastructure.batch;

import app.backend.jamo.diary.infrastructure.config.SentenceFeedbackBatchProperties;
import app.backend.jamo.diary.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 발행 완료 outbox_event row retention cleanup — code-reviewer L3 (PR #71) 박제 후속.
 *
 * <p>Default 7일 — 운영 추적 / 장애 분석 윈도우. 발행 실패 / 미발행 row (`published_at IS NULL`) 는
 * 영향 X (별 모니터링 영역).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PublishedOutboxCleanupBatch {

    private final SpringDataOutboxEventRepository repository;
    private final SentenceFeedbackBatchProperties properties;
    private final Clock clock;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void run() {
        try {
            Instant cutoff = Instant.now(clock).minus(Duration.ofDays(properties.outboxRetentionDays()));
            int deleted = repository.deletePublishedBefore(cutoff);
            if (deleted > 0) {
                log.info("outbox_event published cleanup deleted={} retentionDays={}",
                    deleted, properties.outboxRetentionDays());
            }
        } catch (Exception ex) {
            log.error("outbox_event published cleanup failed", ex);
        }
    }
}
