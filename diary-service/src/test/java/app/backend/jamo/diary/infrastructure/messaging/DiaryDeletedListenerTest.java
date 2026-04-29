package app.backend.jamo.diary.infrastructure.messaging;

import app.backend.jamo.contracts.event.diary.DiaryDeleted;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiaryDeletedListenerTest {

    private SentenceFeedbackRepository sentenceFeedbackRepository;
    private SpringDataProcessedEventRepository processedEventRepository;
    private ObjectMapper objectMapper;
    private Acknowledgment ack;
    private DiaryDeletedListener listener;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-29T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setup() {
        sentenceFeedbackRepository = mock(SentenceFeedbackRepository.class);
        processedEventRepository = mock(SpringDataProcessedEventRepository.class);
        objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ack = mock(Acknowledgment.class);
        listener = new DiaryDeletedListener(
            sentenceFeedbackRepository, processedEventRepository, objectMapper, fixedClock
        );
    }

    @Test
    void happy_path_first_delivery_cascades_then_acks() throws Exception {
        UUID diaryId = UUID.randomUUID();
        DiaryDeleted event = new DiaryDeleted(
            "evt-1", Instant.parse("2026-04-29T11:00:00Z"),
            diaryId.toString(), UUID.randomUUID().toString()
        );
        String payload = objectMapper.writeValueAsString(event);
        when(processedEventRepository.existsByConsumerIdAndEventId(DiaryDeletedListener.CONSUMER_ID, "evt-1"))
            .thenReturn(false);
        when(sentenceFeedbackRepository.deleteAllByDiaryId(diaryId)).thenReturn(3);

        listener.onMessage(payload, ack);

        verify(sentenceFeedbackRepository).deleteAllByDiaryId(diaryId);

        ArgumentCaptor<ProcessedEventJpaEntity> captor = ArgumentCaptor.forClass(ProcessedEventJpaEntity.class);
        verify(processedEventRepository).save(captor.capture());
        ProcessedEventJpaEntity row = captor.getValue();
        assertThat(row.getConsumerId()).isEqualTo(DiaryDeletedListener.CONSUMER_ID);
        assertThat(row.getEventId()).isEqualTo("evt-1");
        assertThat(row.getProcessedAt()).isEqualTo(Instant.parse("2026-04-29T12:00:00Z"));

        verify(ack).acknowledge();
    }

    @Test
    void duplicate_delivery_skips_cascade_and_acks() throws Exception {
        UUID diaryId = UUID.randomUUID();
        DiaryDeleted event = new DiaryDeleted(
            "evt-2", Instant.parse("2026-04-29T11:00:00Z"),
            diaryId.toString(), UUID.randomUUID().toString()
        );
        String payload = objectMapper.writeValueAsString(event);
        when(processedEventRepository.existsByConsumerIdAndEventId(DiaryDeletedListener.CONSUMER_ID, "evt-2"))
            .thenReturn(true);

        listener.onMessage(payload, ack);

        verify(sentenceFeedbackRepository, never()).deleteAllByDiaryId(eq(diaryId));
        verify(processedEventRepository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void cascade_failure_propagates_exception_and_does_not_ack_and_does_not_record_processed() throws Exception {
        // security-reviewer H-1 + test-reviewer H1 — try/finally 제거 후 핵심 보증:
        // 예외가 caller (Spring KafkaListener) 로 propagate → @Transactional rollback → ack 보류 →
        // Spring DefaultErrorHandler 가 retry. cascade 영구 누락 (GDPR 위반) 회피.
        UUID diaryId = UUID.randomUUID();
        DiaryDeleted event = new DiaryDeleted(
            "evt-3", Instant.parse("2026-04-29T11:00:00Z"),
            diaryId.toString(), UUID.randomUUID().toString()
        );
        String payload = objectMapper.writeValueAsString(event);
        when(processedEventRepository.existsByConsumerIdAndEventId(DiaryDeletedListener.CONSUMER_ID, "evt-3"))
            .thenReturn(false);
        when(sentenceFeedbackRepository.deleteAllByDiaryId(diaryId))
            .thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> listener.onMessage(payload, ack))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("db down");

        verify(processedEventRepository, never()).save(any());
        verify(ack, never()).acknowledge();
    }

    @Test
    void non_DiaryDeleted_payload_in_topic_is_skipped_and_acked() {
        // diary-events 토픽은 DiaryCreated / SentenceFeedback*3 등 다른 type 도 흐름. 본 listener 가
        // deserialize 실패 → null → ack skip (다른 listener 가 처리, code-reviewer C1 후속 분기 정책).
        String foreignPayload = "{\"foreignField\":\"x\"}";

        listener.onMessage(foreignPayload, ack);

        verify(sentenceFeedbackRepository, never()).deleteAllByDiaryId(any());
        verify(processedEventRepository, never()).save(any());
        verify(ack).acknowledge();
    }
}
