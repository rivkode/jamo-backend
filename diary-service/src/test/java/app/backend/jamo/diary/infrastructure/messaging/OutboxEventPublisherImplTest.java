package app.backend.jamo.diary.infrastructure.messaging;

import app.backend.jamo.contracts.event.diary.SentenceFeedbackAccepted;
import app.backend.jamo.contracts.event.diary.SentenceFeedbackRejected;
import app.backend.jamo.contracts.event.diary.SentenceFeedbackRequested;
import app.backend.jamo.contracts.event.identity.UserDataPurged;
import app.backend.jamo.diary.infrastructure.persistence.entity.OutboxEventJpaEntity;
import app.backend.jamo.diary.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * OutboxEventPublisherImpl 단위 테스트 — record class 별 entity build 분기 + JSON 직렬화.
 *
 * <p>Spring Boot 의 ObjectMapper 자동 구성 (jackson-datatype-jsr310 등록) 정합을 위해 본 테스트는
 * 자체 ObjectMapper + JavaTimeModule 명시 등록.
 */
class OutboxEventPublisherImplTest {

    private SpringDataOutboxEventRepository repository;
    private OutboxEventPublisherImpl publisher;
    private ObjectMapper objectMapper;
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-29T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setup() {
        repository = mock(SpringDataOutboxEventRepository.class);
        // Spring Boot 자동 구성과 정합 — JavaTimeModule + WRITE_DATES_AS_TIMESTAMPS=false
        // (test-reviewer H4 — 운영 Kafka payload 직렬화 동작과 일치 검증)
        objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        publisher = new OutboxEventPublisherImpl(repository, objectMapper, fixedClock);
    }

    @Test
    void publish_SentenceFeedbackRequested_routes_to_diary_events_topic() throws Exception {
        SentenceFeedbackRequested event = new SentenceFeedbackRequested(
            "evt-1", Instant.parse("2026-04-29T11:59:00Z"),
            "fb-100", "user-1"
        );

        publisher.publish(event);

        OutboxEventJpaEntity captured = captureSaved();
        assertThat(captured.getEventId()).isEqualTo("evt-1");
        assertThat(captured.getAggregateType()).isEqualTo("sentence_feedback");
        assertThat(captured.getAggregateId()).isEqualTo("fb-100");
        assertThat(captured.getType()).isEqualTo(SentenceFeedbackRequested.class.getName());
        assertThat(captured.getTopic()).isEqualTo("diary-events");
        assertThat(captured.getCreatedAt()).isEqualTo(Instant.parse("2026-04-29T12:00:00Z"));
        assertThat(captured.getPublishedAt()).isNull();

        JsonNode payloadJson = objectMapper.readTree(captured.getPayload());
        assertThat(payloadJson.get("eventId").asText()).isEqualTo("evt-1");
        assertThat(payloadJson.get("feedbackId").asText()).isEqualTo("fb-100");
        // test-reviewer H4 — Instant 가 ISO-8601 string 으로 직렬화 (Spring Boot 기본 동작 정합)
        assertThat(payloadJson.get("occurredAt").asText()).isEqualTo("2026-04-29T11:59:00Z");
    }

    @Test
    void publish_SentenceFeedbackAccepted_routes_to_diary_events() {
        SentenceFeedbackAccepted event = new SentenceFeedbackAccepted(
            "evt-2", Instant.parse("2026-04-29T11:00:00Z"),
            "fb-200", "user-1", "sug-1"
        );

        publisher.publish(event);

        OutboxEventJpaEntity captured = captureSaved();
        assertThat(captured.getType()).isEqualTo(SentenceFeedbackAccepted.class.getName());
        assertThat(captured.getTopic()).isEqualTo("diary-events");
        assertThat(captured.getAggregateId()).isEqualTo("fb-200");
    }

    @Test
    void publish_SentenceFeedbackRejected_routes_to_diary_events() {
        SentenceFeedbackRejected event = new SentenceFeedbackRejected(
            "evt-3", Instant.parse("2026-04-29T10:00:00Z"),
            "fb-300", "user-1"
        );

        publisher.publish(event);

        OutboxEventJpaEntity captured = captureSaved();
        assertThat(captured.getType()).isEqualTo(SentenceFeedbackRejected.class.getName());
        assertThat(captured.getTopic()).isEqualTo("diary-events");
    }

    @Test
    void publish_UserDataPurged_routes_to_user_events_topic() {
        UserDataPurged event = new UserDataPurged(
            "evt-4", Instant.parse("2026-04-29T09:00:00Z"),
            UUID.randomUUID().toString(), "diary"
        );

        publisher.publish(event);

        OutboxEventJpaEntity captured = captureSaved();
        assertThat(captured.getType()).isEqualTo(UserDataPurged.class.getName());
        assertThat(captured.getTopic()).isEqualTo("user-events");
        assertThat(captured.getAggregateType()).isEqualTo("user");
        assertThat(captured.getAggregateId()).isEqualTo(event.userId());
    }

    @Test
    void publish_unsupported_type_throws_and_does_not_save() {
        // 도메인 record 4종 외 타입은 build() 분기 IllegalArgumentException. 직렬화는 통과 (record 라
        // ObjectMapper 가 정상 처리) → instanceof chain 끝에서 throw. test-reviewer C1 — save 미호출
        // 명시 검증 (verifyNoInteractions / never().save(any) 둘 다).
        Foreign foreign = new Foreign("x");
        assertThatThrownBy(() -> publisher.publish(foreign))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unsupported outbox event type");
        verify(repository, never()).save(any());
        verifyNoInteractions(repository);
    }

    /** ObjectMapper 직렬화는 통과하지만 build() 분기에서 거부되는 케이스 분리 검증용. */
    record Foreign(String x) {
    }

    private OutboxEventJpaEntity captureSaved() {
        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
