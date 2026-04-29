package app.backend.jamo.diary.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.Instant;

/**
 * processed_event 테이블 매핑 — Kafka Consumer 멱등성.
 *
 * <p>CLAUDE.md NEVER: "Kafka Consumer 멱등성 미처리 (`ProcessedEvent` 테이블 필수)".
 *
 * <p>Listener 가 이벤트 처리 직전에 {@code (consumer_id, event_id)} 가 이미 있으면 skip.
 * 같은 이벤트라도 여러 listener 가 다른 의미로 처리할 수 있으므로 복합 UNIQUE.
 */
@Entity
@Getter
@Table(
    name = "processed_event",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_processed_event_consumer_event",
        columnNames = {"consumer_id", "event_id"}
    )
)
public class ProcessedEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "consumer_id", nullable = false, length = 128)
    private String consumerId;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventJpaEntity() {
    }

    public ProcessedEventJpaEntity(String consumerId, String eventId, Instant processedAt) {
        this.consumerId = consumerId;
        this.eventId = eventId;
        this.processedAt = processedAt;
    }
}
