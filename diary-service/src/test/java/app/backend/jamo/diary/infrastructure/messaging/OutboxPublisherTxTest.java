package app.backend.jamo.diary.infrastructure.messaging;

import app.backend.jamo.contracts.event.diary.DiaryDeleted;
import app.backend.jamo.diary.infrastructure.persistence.entity.OutboxEventJpaEntity;
import app.backend.jamo.diary.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OutboxPublisherTx — code-reviewer C1 재검증 후 추가된 Medium 1 (producer-side header 부착 회귀 안전망).
 *
 * <p>{@code event-type} 헤더는 listener 의 type filter 에 필수. 누군가 본 라인 (record.headers().add) 을
 * 실수로 제거하면 모든 cascade listener 가 skip → silent failure. 단위 테스트로 헤더 부착을 박제.
 */
class OutboxPublisherTxTest {

    private SpringDataOutboxEventRepository repository;
    private KafkaTemplate<String, String> kafkaTemplate;
    private OutboxPublisherTx tx;

    private final Instant now = Instant.parse("2026-04-30T12:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(SpringDataOutboxEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> mockTemplate = mock(KafkaTemplate.class);
        kafkaTemplate = mockTemplate;
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        tx = new OutboxPublisherTx(repository, kafkaTemplate, clock);
    }

    @Test
    void publishOne_attaches_event_type_header_with_record_fqn() {
        OutboxEventJpaEntity row = new OutboxEventJpaEntity(
            "evt-1", "diary", "d-1",
            DiaryDeleted.class.getName(),
            KafkaTopics.DIARY_EVENTS,
            "{}",
            now
        );
        // Reflection 으로 id 주입 어려우므로 findById 의 stub 만 활용
        when(repository.findById(any())).thenReturn(Optional.of(row));
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        tx.publishOne(1L);

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<ProducerRecord<String, String>> captor =
            (ArgumentCaptor) ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();

        Header header = sent.headers().lastHeader(OutboxPublisherTx.EVENT_TYPE_HEADER);
        assertThat(header).isNotNull();
        assertThat(new String(header.value(), StandardCharsets.UTF_8))
            .isEqualTo(DiaryDeleted.class.getName());
        assertThat(sent.topic()).isEqualTo(KafkaTopics.DIARY_EVENTS);
        assertThat(sent.key()).isEqualTo("d-1");
        assertThat(sent.value()).isEqualTo("{}");

        verify(repository).markPublished(any(), any());
    }

    @Test
    void publishOne_skips_when_already_published() {
        OutboxEventJpaEntity row = new OutboxEventJpaEntity(
            "evt-1", "diary", "d-1",
            DiaryDeleted.class.getName(),
            KafkaTopics.DIARY_EVENTS,
            "{}",
            now
        );
        row.markPublished(now.minusSeconds(60));  // 이미 published
        when(repository.findById(any())).thenReturn(Optional.of(row));

        tx.publishOne(1L);

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        verify(repository, never()).markPublished(any(), any());
    }
}
