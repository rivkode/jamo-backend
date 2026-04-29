package app.backend.jamo.diary.infrastructure.messaging;

import app.backend.jamo.contracts.event.identity.UserDataPurged;
import app.backend.jamo.contracts.event.identity.UserWithdrawalRequested;
import app.backend.jamo.diary.domain.repository.OutboxEventPublisher;
import app.backend.jamo.diary.domain.repository.SentenceFeedbackRepository;
import app.backend.jamo.diary.infrastructure.persistence.entity.ProcessedEventJpaEntity;
import app.backend.jamo.diary.infrastructure.persistence.repository.SpringDataProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserWithdrawalRequestedListenerTest {

    private SentenceFeedbackRepository sentenceFeedbackRepository;
    private SpringDataProcessedEventRepository processedEventRepository;
    private OutboxEventPublisher outboxEventPublisher;
    private ObjectMapper objectMapper;
    private Acknowledgment ack;
    private UserWithdrawalRequestedListener listener;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-29T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setup() {
        sentenceFeedbackRepository = mock(SentenceFeedbackRepository.class);
        processedEventRepository = mock(SpringDataProcessedEventRepository.class);
        outboxEventPublisher = mock(OutboxEventPublisher.class);
        objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ack = mock(Acknowledgment.class);
        listener = new UserWithdrawalRequestedListener(
            sentenceFeedbackRepository, processedEventRepository,
            outboxEventPublisher, objectMapper, fixedClock
        );
    }

    @Test
    void happy_path_cascades_records_processed_and_publishes_UserDataPurged() throws Exception {
        UUID userId = UUID.randomUUID();
        UserWithdrawalRequested event = new UserWithdrawalRequested(
            "evt-1", Instant.parse("2026-04-29T11:00:00Z"), userId.toString()
        );
        String payload = objectMapper.writeValueAsString(event);
        when(processedEventRepository.existsByConsumerIdAndEventId(
            UserWithdrawalRequestedListener.CONSUMER_ID, "evt-1")).thenReturn(false);
        when(sentenceFeedbackRepository.deleteAllByUserId(userId)).thenReturn(5);

        listener.onMessage(payload, ack);

        verify(sentenceFeedbackRepository).deleteAllByUserId(userId);
        verify(processedEventRepository).save(any(ProcessedEventJpaEntity.class));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisher).publish(captor.capture());
        UserDataPurged purged = (UserDataPurged) captor.getValue();
        assertThat(purged.userId()).isEqualTo(userId.toString());
        assertThat(purged.sourceService()).isEqualTo("diary");
        assertThat(purged.occurredAt()).isEqualTo(Instant.parse("2026-04-29T12:00:00Z"));
        assertThat(purged.eventId()).isNotBlank();

        verify(ack).acknowledge();
    }

    @Test
    void duplicate_delivery_skips_cascade_and_skips_publish() throws Exception {
        UUID userId = UUID.randomUUID();
        UserWithdrawalRequested event = new UserWithdrawalRequested(
            "evt-2", Instant.parse("2026-04-29T11:00:00Z"), userId.toString()
        );
        String payload = objectMapper.writeValueAsString(event);
        when(processedEventRepository.existsByConsumerIdAndEventId(
            UserWithdrawalRequestedListener.CONSUMER_ID, "evt-2")).thenReturn(true);

        listener.onMessage(payload, ack);

        verify(sentenceFeedbackRepository, never()).deleteAllByUserId(userId);
        verify(outboxEventPublisher, never()).publish(any());
        verify(ack).acknowledge();
    }

    @Test
    void cascade_returning_zero_still_publishes_UserDataPurged_for_saga_completion() throws Exception {
        // sentence-feedback 데이터를 한 번도 가진 적 없는 사용자도 회신 발행해야 identity-service Saga 완결.
        UUID userId = UUID.randomUUID();
        UserWithdrawalRequested event = new UserWithdrawalRequested(
            "evt-3", Instant.parse("2026-04-29T11:00:00Z"), userId.toString()
        );
        String payload = objectMapper.writeValueAsString(event);
        when(processedEventRepository.existsByConsumerIdAndEventId(
            UserWithdrawalRequestedListener.CONSUMER_ID, "evt-3")).thenReturn(false);
        when(sentenceFeedbackRepository.deleteAllByUserId(userId)).thenReturn(0);

        listener.onMessage(payload, ack);

        verify(outboxEventPublisher).publish(any(UserDataPurged.class));
        verify(ack).acknowledge();
    }

    @Test
    void cascade_failure_propagates_and_skips_publish_and_does_not_ack() throws Exception {
        UUID userId = UUID.randomUUID();
        UserWithdrawalRequested event = new UserWithdrawalRequested(
            "evt-4", Instant.parse("2026-04-29T11:00:00Z"), userId.toString()
        );
        String payload = objectMapper.writeValueAsString(event);
        when(processedEventRepository.existsByConsumerIdAndEventId(
            UserWithdrawalRequestedListener.CONSUMER_ID, "evt-4")).thenReturn(false);
        when(sentenceFeedbackRepository.deleteAllByUserId(userId))
            .thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> listener.onMessage(payload, ack))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("db down");

        verify(outboxEventPublisher, never()).publish(any());
        verify(ack, never()).acknowledge();
    }

    @Test
    void publish_failure_propagates_after_cascade_and_does_not_ack() throws Exception {
        UUID userId = UUID.randomUUID();
        UserWithdrawalRequested event = new UserWithdrawalRequested(
            "evt-5", Instant.parse("2026-04-29T11:00:00Z"), userId.toString()
        );
        String payload = objectMapper.writeValueAsString(event);
        when(processedEventRepository.existsByConsumerIdAndEventId(
            UserWithdrawalRequestedListener.CONSUMER_ID, "evt-5")).thenReturn(false);
        when(sentenceFeedbackRepository.deleteAllByUserId(userId)).thenReturn(2);
        doThrow(new RuntimeException("outbox table locked"))
            .when(outboxEventPublisher).publish(any(UserDataPurged.class));

        assertThatThrownBy(() -> listener.onMessage(payload, ack))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("outbox table locked");

        verify(ack, never()).acknowledge();
    }

    @Test
    void non_UserWithdrawalRequested_payload_in_topic_is_skipped_and_acked() {
        // user-events 토픽에 본 서비스 자체 발행한 UserDataPurged 회신도 흐를 수 있음 — deserialize 실패
        // → null → ack skip.
        String foreignPayload = "{\"sourceService\":\"diary\"}";

        listener.onMessage(foreignPayload, ack);

        verify(sentenceFeedbackRepository, never()).deleteAllByUserId(any());
        verify(outboxEventPublisher, never()).publish(any());
        verify(ack).acknowledge();
    }
}
