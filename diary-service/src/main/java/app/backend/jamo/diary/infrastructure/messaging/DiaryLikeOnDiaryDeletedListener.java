package app.backend.jamo.diary.infrastructure.messaging;

import app.backend.jamo.contracts.event.diary.DiaryDeleted;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.repository.DiaryLikeRepository;
import app.backend.jamo.diary.infrastructure.persistence.entity.ProcessedEventJpaEntity;
import app.backend.jamo.diary.infrastructure.persistence.repository.SpringDataProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * DiaryDeleted Saga consumer (diary_likes 자체 cascade) — 박제 diary-domain-policy §9 / §10.
 *
 * <p>diary-service 가 자체 발행한 {@link DiaryDeleted} 를 자체 구독해 diary_likes 중 같은 diary 의 row 를
 * hard-delete. {@link SentenceFeedbackOnDiaryDeletedListener} 와 같은 토픽 / 다른 consumer group / 다른
 * CONSUMER_ID — 단일 책임 (각 sub-domain 별 cascade 분리).
 *
 * <p><b>Wire format</b>: producer raw JSON String + Kafka header {@code event-type} (record FQN). listener 가
 * 헤더로 type filter — DiaryCreated 와 DiaryDeleted 가 같은 wire 필드 (eventId/occurredAt/diaryId/userId) 라
 * payload 만으로는 구분 불가 (code-reviewer C1). 다른 type / 헤더 누락 → ack skip.
 *
 * <p>멱등성: {@code (consumer_id, event_id)} ProcessedEvent 검사. 트랜잭션 안에서 cascade + ProcessedEvent
 * insert → 재시도 안전.
 *
 * <p><b>Ack 정책</b>: cascade 트랜잭션 commit 후에만 ack. 예외 발생 시 Spring Kafka {@code DefaultErrorHandler}
 * retry — transient DB 장애가 cascade 영구 누락으로 전환되는 anti-pattern 회피.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiaryLikeOnDiaryDeletedListener {

    static final String CONSUMER_ID = "diary-service.diary-like.DiaryLikeOnDiaryDeletedListener";

    private final DiaryLikeRepository diaryLikeRepository;
    private final SpringDataProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @KafkaListener(
        topics = KafkaTopics.DIARY_EVENTS,
        groupId = "${spring.kafka.consumer.group-id}.diary-deleted-diary-like"
    )
    @Transactional
    public void onMessage(@Payload String payload,
                          @Header(name = OutboxPublisherTx.EVENT_TYPE_HEADER, required = false) String eventType,
                          Acknowledgment ack) {
        if (!DiaryDeleted.class.getName().equals(eventType)) {
            ack.acknowledge();
            return;
        }
        DiaryDeleted event = deserialize(payload);
        if (event == null) {
            ack.acknowledge();
            return;
        }
        handle(event);
        ack.acknowledge();
    }

    private DiaryDeleted deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, DiaryDeleted.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            log.debug("DiaryLikeOnDiaryDeletedListener skipping non-DiaryDeleted payload: {}",
                ex.getMessage());
            return null;
        }
    }

    private void handle(DiaryDeleted event) {
        if (processedEventRepository.existsByConsumerIdAndEventId(CONSUMER_ID, event.eventId())) {
            return;
        }
        DiaryId diaryId = DiaryId.fromString(event.diaryId());
        int deleted = diaryLikeRepository.deleteAllByDiaryId(diaryId);
        processedEventRepository.save(
            new ProcessedEventJpaEntity(CONSUMER_ID, event.eventId(), Instant.now(clock))
        );
        log.info("diary_likes cascade by DiaryDeleted diaryId={} deleted={}", diaryId.asString(), deleted);
    }
}
