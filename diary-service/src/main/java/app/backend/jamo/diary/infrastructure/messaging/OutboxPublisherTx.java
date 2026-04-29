package app.backend.jamo.diary.infrastructure.messaging;

import app.backend.jamo.diary.infrastructure.persistence.entity.OutboxEventJpaEntity;
import app.backend.jamo.diary.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Outbox publish 의 트랜잭션 경계 분리 — {@link OutboxPoller} 의 {@code @Scheduled} 메서드와 동일 클래스
 * 내 self-call 을 회피하기 위한 별 bean (Spring AOP self-call proxy 미적용 이슈).
 *
 * <p>두 메서드 모두 별 트랜잭션:
 * <ul>
 *   <li>{@link #findPendingIds()} — readOnly. {@code SKIP LOCKED} 로 다른 인스턴스가 잠근 row 회피</li>
 *   <li>{@link #publishOne(Long)} — write. row 재잠금 → Kafka send (timeout 명시) → 명시 UPDATE
 *       markPublished. 실패 시 markPublished 미호출 → 다음 polling 사이클 재시도</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherTx {

    private static final int BATCH_SIZE = 100;
    /** Kafka send 단건 timeout — broker 무응답으로 poller 영구 정지 회피 (code-reviewer L3). */
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final SpringDataOutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Long> findPendingIds() {
        return repository.findUnpublishedForUpdate(BATCH_SIZE).stream()
            .map(OutboxEventJpaEntity::getId)
            .toList();
    }

    @Transactional
    public void publishOne(Long id) {
        OutboxEventJpaEntity row = repository.findById(id).orElse(null);
        if (row == null || row.getPublishedAt() != null) {
            // 다른 인스턴스가 이미 처리했거나 사라짐 — noop
            return;
        }
        try {
            kafkaTemplate.send(row.getTopic(), row.getAggregateId(), row.getPayload())
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            repository.markPublished(id, Instant.now(clock));
        } catch (Exception ex) {
            // markPublished 미호출 → published_at 미갱신 → 다음 polling 사이클 재시도. 트랜잭션 commit 자체는
            // 진행 (rollback 시 무의미한 SELECT 만 있어 OK). throw 하지 않아 batch 의 다른 row 에 영향 없음.
            log.warn("outbox publish failed eventId={} topic={} type={}: {}",
                row.getEventId(), row.getTopic(), row.getType(), ex.toString());
        }
    }
}
