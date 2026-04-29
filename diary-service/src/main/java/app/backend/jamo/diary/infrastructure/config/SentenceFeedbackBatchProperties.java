package app.backend.jamo.diary.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * sentence-feedback 배치 / cleanup 작업 설정 (D-a-5-impl-batch).
 *
 * <p>박제: decisions/diary/sentence-feedback-batch-decisions.md.
 *
 * @param expireInterval                  EXPIRED 전이 배치 주기 (default 5분 — 박제 §3 24h TTL 대비 정확성·DB부하 trade-off)
 * @param chunkSize                       단일 사이클 처리 row 수 (default 100 — OutboxPoller 정합)
 * @param retentionDays                   sentence_feedback final 상태 보존 일수 (default 90, 박제 §14)
 * @param processedEventRetentionDays     ProcessedEvent retention (default 30 — Kafka offset 진행 후 안전)
 * @param outboxRetentionDays             published Outbox row retention (default 7 — 운영 추적 윈도우)
 */
@ConfigurationProperties(prefix = "jamo.sentence-feedback.batch")
public record SentenceFeedbackBatchProperties(
    Duration expireInterval,
    int chunkSize,
    int retentionDays,
    int processedEventRetentionDays,
    int outboxRetentionDays
) {
    public SentenceFeedbackBatchProperties {
        if (expireInterval == null || expireInterval.isNegative() || expireInterval.isZero()) {
            throw new IllegalArgumentException("expireInterval must be positive");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }
        if (processedEventRetentionDays <= 0) {
            throw new IllegalArgumentException("processedEventRetentionDays must be positive");
        }
        if (outboxRetentionDays <= 0) {
            throw new IllegalArgumentException("outboxRetentionDays must be positive");
        }
    }
}
