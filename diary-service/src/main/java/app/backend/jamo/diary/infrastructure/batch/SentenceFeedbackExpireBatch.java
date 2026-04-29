package app.backend.jamo.diary.infrastructure.batch;

import app.backend.jamo.diary.application.service.sentencefeedback.ExpireSentenceFeedbackService;
import app.backend.jamo.diary.infrastructure.config.SentenceFeedbackBatchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SUGGESTED → EXPIRED 전이 배치 (D-a-5-impl-batch §3).
 *
 * <p>주기 default 5분 — 박제 §3 (TTL 24h) 의 정확성 (사용자가 24h+5분 후 EXPIRED 확인) vs DB 부하
 * trade-off. 운영 envvar `SENTENCE_FEEDBACK_EXPIRE_INTERVAL` 로 조정.
 *
 * <p>한 사이클 = chunk 1개 (default 100 row, OutboxPoller 정합) 처리. throw 시 다음 사이클까지 대기 —
 * batch 자체 자동 재시도 없음 (Spring `@Scheduled` 의 fire-and-forget 모델).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SentenceFeedbackExpireBatch {

    private final ExpireSentenceFeedbackService service;
    private final SentenceFeedbackBatchProperties properties;

    @Scheduled(fixedDelayString = "#{@sentenceFeedbackBatchProperties.expireInterval}")
    public void run() {
        try {
            ExpireSentenceFeedbackService.Result result = service.run(properties.chunkSize());
            if (result.candidates() > 0) {
                log.info("sentence-feedback EXPIRED batch candidates={} expired={} skipped={}",
                    result.candidates(), result.expired(), result.skipped());
            }
        } catch (Exception ex) {
            // batch 사이클 자체 실패 — 다음 사이클까지 대기. invariant 위반 / DB 장애 등.
            log.error("sentence-feedback EXPIRED batch failed", ex);
        }
    }
}
