package app.backend.jamo.diary.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

/**
 * outbox_event 테이블 매핑.
 *
 * <p>Outbox 패턴 (CLAUDE.md NEVER: Outbox 없이 도메인 이벤트 발행 금지):
 * <ol>
 *   <li>Application Service 가 Aggregate persist 와 동일 트랜잭션에서 row insert</li>
 *   <li>{@code OutboxPoller} 가 미발행 row ({@code published_at IS NULL}) 를 polling → Kafka send</li>
 *   <li>send 성공 시 {@code published_at} 채움</li>
 * </ol>
 *
 * <p>{@code event_id} UNIQUE — 멱등 publish (poller 재시작 / 중복 polling 시 INSERT 단계에서 차단).
 * payload 는 contracts record 의 Jackson 직렬화 결과 (JsonNode → string).
 */
@Entity
@Getter
@Table(name = "outbox_event")
public class OutboxEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36, unique = true)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "type", nullable = false, length = 128)
    private String type;

    @Column(name = "topic", nullable = false, length = 64)
    private String topic;

    /**
     * Outbox payload — {@link app.backend.jamo.diary.infrastructure.messaging.OutboxEventPublisherImpl} 가
     * 미리 Jackson 으로 직렬화한 raw JSON 문자열. {@code @JdbcTypeCode(SqlTypes.JSON)} 미적용 — 이미
     * 직렬화된 String 을 다시 Hibernate JSON 직렬화 layer 가 처리하면 이중 escape (code-reviewer M2).
     * MySQL JSON 컬럼이 들어오는 String 을 자체 검증/저장.
     */
    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEventJpaEntity() {
    }

    public OutboxEventJpaEntity(String eventId, String aggregateType, String aggregateId,
                                String type, String topic, String payload, Instant createdAt) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.type = type;
        this.topic = topic;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
