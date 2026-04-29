package app.backend.jamo.diary.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * outbox_event 의 미발행 row 를 주기적으로 polling → Kafka 발행 → {@code published_at} 채움.
 *
 * <p>주기 5s (운영 시 envvar 조정). batch 100 row.
 *
 * <p><b>트랜잭션 분리 (code-reviewer C3)</b>: read 와 단건 publish 를 {@link OutboxPublisherTx} 별 bean 으로
 * 분리. 단건 publish 는 별 트랜잭션 + {@code FOR UPDATE SKIP LOCKED} (H1 — 다중 인스턴스 안전) +
 * 명시 UPDATE markPublished. 한 row 의 send 실패가 다른 row 에 영향 X (catch + swallow,
 * markPublished 미호출 = 다음 사이클 재시도).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxPublisherTx tx;

    @Scheduled(fixedDelayString = "${jamo.outbox.poll-interval-ms:5000}")
    public void publishPending() {
        List<Long> ids = tx.findPendingIds();
        if (ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            tx.publishOne(id);
        }
    }
}
