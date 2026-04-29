package app.backend.jamo.diary.infrastructure.batch;

import app.backend.jamo.diary.application.service.sentencefeedback.CleanupSentenceFeedbackService;
import app.backend.jamo.diary.infrastructure.config.SentenceFeedbackBatchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * sentence-feedback 90일 retention cleanup (§14).
 *
 * <p>cron 매일 02:00 KST (오프피크). chunk 1개씩만 — 큰 backlog 시 여러 일에 걸쳐 점진 삭제 (운영 안전).
 * 후속 검토: backlog 가 큰 경우 단일 사이클 chunk 반복 vs cron interval 단축.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SentenceFeedbackCleanupBatch {

    private final CleanupSentenceFeedbackService service;
    private final SentenceFeedbackBatchProperties properties;

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            int deleted = service.run(properties.retentionDays(), properties.chunkSize());
            if (deleted > 0) {
                log.info("sentence-feedback retention cleanup deleted={} retentionDays={}",
                    deleted, properties.retentionDays());
            }
        } catch (Exception ex) {
            log.error("sentence-feedback retention cleanup failed", ex);
        }
    }
}
