package app.backend.jamo.diary.infrastructure.messaging;

import app.backend.jamo.contracts.event.diary.DiaryDeleted;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.repository.DiaryLikeRepository;
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

/**
 * DiaryLikeOnDiaryDeletedListener — sentence-feedback 의 정합 listener test 패턴.
 */
class DiaryLikeOnDiaryDeletedListenerTest {

    private DiaryLikeRepository diaryLikeRepository;
    private SpringDataProcessedEventRepository processedEventRepository;
    private ObjectMapper objectMapper;
    private Acknowledgment ack;
    private DiaryLikeOnDiaryDeletedListener listener;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-30T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setup() {
        diaryLikeRepository = mock(DiaryLikeRepository.class);
        processedEventRepository = mock(SpringDataProcessedEventRepository.class);
        objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ack = mock(Acknowledgment.class);
        listener = new DiaryLikeOnDiaryDeletedListener(
            diaryLikeRepository, processedEventRepository, objectMapper, fixedClock
        );
    }

    @Test
    void happy_path_first_delivery_cascades_then_acks() throws Exception {
        UUID diaryId = UUID.randomUUID();
        DiaryDeleted event = new DiaryDeleted(
            "evt-1", Instant.parse("2026-04-30T11:00:00Z"),
            diaryId.toString(), UUID.randomUUID().toString()
        );
        String payload = objectMapper.writeValueAsString(event);
        when(processedEventRepository.existsByConsumerIdAndEventId(
            DiaryLikeOnDiaryDeletedListener.CONSUMER_ID, "evt-1")).thenReturn(false);
        when(diaryLikeRepository.deleteAllByDiaryId(DiaryId.of(diaryId))).thenReturn(5);

        listener.onMessage(payload, DiaryDeleted.class.getName(), ack);

        verify(diaryLikeRepository).deleteAllByDiaryId(DiaryId.of(diaryId));

        ArgumentCaptor<ProcessedEventJpaEntity> captor = ArgumentCaptor.forClass(ProcessedEventJpaEntity.class);
        verify(processedEventRepository).save(captor.capture());
        ProcessedEventJpaEntity row = captor.getValue();
        assertThat(row.getConsumerId()).isEqualTo(DiaryLikeOnDiaryDeletedListener.CONSUMER_ID);
        assertThat(row.getEventId()).isEqualTo("evt-1");
        assertThat(row.getProcessedAt()).isEqualTo(Instant.parse("2026-04-30T12:00:00Z"));

        verify(ack).acknowledge();
    }

    @Test
    void duplicate_delivery_skips_cascade_and_acks() throws Exception {
        UUID diaryId = UUID.randomUUID();
        DiaryDeleted event = new DiaryDeleted(
            "evt-2", Instant.parse("2026-04-30T11:00:00Z"),
            diaryId.toString(), UUID.randomUUID().toString()
        );
        String payload = objectMapper.writeValueAsString(event);
        when(processedEventRepository.existsByConsumerIdAndEventId(
            DiaryLikeOnDiaryDeletedListener.CONSUMER_ID, "evt-2")).thenReturn(true);

        listener.onMessage(payload, DiaryDeleted.class.getName(), ack);

        verify(diaryLikeRepository, never()).deleteAllByDiaryId(any());
        verify(processedEventRepository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void cascade_failure_propagates_and_does_not_ack() throws Exception {
        UUID diaryId = UUID.randomUUID();
        DiaryDeleted event = new DiaryDeleted(
            "evt-3", Instant.parse("2026-04-30T11:00:00Z"),
            diaryId.toString(), UUID.randomUUID().toString()
        );
        String payload = objectMapper.writeValueAsString(event);
        when(processedEventRepository.existsByConsumerIdAndEventId(
            DiaryLikeOnDiaryDeletedListener.CONSUMER_ID, "evt-3")).thenReturn(false);
        when(diaryLikeRepository.deleteAllByDiaryId(eq(DiaryId.of(diaryId))))
            .thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> listener.onMessage(payload, DiaryDeleted.class.getName(), ack))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("db down");

        verify(processedEventRepository, never()).save(any());
        verify(ack, never()).acknowledge();
    }

    @Test
    void non_DiaryDeleted_payload_in_topic_is_skipped_and_acked() {
        // type 헤더 일치하나 payload 가 DiaryDeleted 가 아닌 변형 케이스 — deserialize 실패 → skip
        String foreignPayload = "{\"foreignField\":\"x\"}";

        listener.onMessage(foreignPayload, DiaryDeleted.class.getName(), ack);

        verify(diaryLikeRepository, never()).deleteAllByDiaryId(any());
        verify(processedEventRepository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void DiaryCreated_payload_with_DiaryCreated_header_is_skipped() {
        // C1 핵심 회귀 — DiaryCreated 와 DiaryDeleted 는 wire 필드 100% 동일.
        // type 헤더가 DiaryDeleted 가 아니면 cascade 발동 X (header filter 첫 가드).
        UUID diaryId = UUID.randomUUID();
        String payload = String.format(
            "{\"eventId\":\"evt-c1\",\"occurredAt\":\"2026-04-30T11:00:00Z\","
                + "\"diaryId\":\"%s\",\"userId\":\"%s\"}",
            diaryId, UUID.randomUUID());

        listener.onMessage(payload, "app.backend.jamo.contracts.event.diary.DiaryCreated", ack);

        verify(diaryLikeRepository, never()).deleteAllByDiaryId(any());
        verify(processedEventRepository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void missing_event_type_header_is_skipped() {
        String payload = "{\"eventId\":\"evt-x\"}";

        listener.onMessage(payload, null, ack);

        verify(diaryLikeRepository, never()).deleteAllByDiaryId(any());
        verify(ack).acknowledge();
    }
}
