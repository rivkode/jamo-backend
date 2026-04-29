package app.backend.jamo.diary.infrastructure.messaging;

import app.backend.jamo.contracts.event.diary.DiaryDeleted;
import app.backend.jamo.diary.domain.repository.SentenceFeedbackRepository;
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
import java.util.UUID;

/**
 * DiaryDeleted Saga consumer (sentence-feedback 자체 cascade) — 박제 sentence-feedback-domain-policy §13.
 *
 * <p>diary-service 가 자체 발행한 {@link DiaryDeleted} 를 자체 구독해 sentence_feedback 중 같은 diary 의
 * row 만 hard-delete. {@code diary_id NULL} row 는 영향 X (작성 전 미리보기 — diary 결합 X).
 *
 * <p><b>본 PR (D-a-3-impl-infra) 에서 rename</b>: 이전 이름 {@code DiaryDeletedListener} → 본 이름. 같은
 * topic 에 다른 sub-domain 의 cascade listener 가 추가됨에 따른 명시적 sub-domain naming
 * ({@link DiaryLikeOnDiaryDeletedListener} 와 정합). CONSUMER_ID 도 갱신 — sentence-feedback PR #75 머지
 * 직후 운영 데이터 미축적이라 이전 이벤트 재처리 영향 없음.
 *
 * <p><b>Wire format</b>: producer 가 raw JSON String 발행 + Kafka header {@code event-type} (record FQN,
 * code-reviewer C1 후속). listener 가 헤더로 type filter — DiaryCreated 와 DiaryDeleted 가 같은 wire 필드라
 * payload 만으로는 구분 불가. 다른 type / 헤더 누락 → ack skip.
 *
 * <p>멱등성: {@code (consumer_id, event_id)} 가 ProcessedEvent 에 이미 있으면 skip. 트랜잭션 안에서
 * cascade + ProcessedEvent insert → 부분 실패 시 재시도 안전.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SentenceFeedbackOnDiaryDeletedListener {

    static final String CONSUMER_ID = "diary-service.sentence-feedback.SentenceFeedbackOnDiaryDeletedListener";

    private final SentenceFeedbackRepository sentenceFeedbackRepository;
    private final SpringDataProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * <p><b>Topic 다형성</b>: {@code diary-events} 토픽은 DiaryDeleted 외 DiaryCreated / CommentCreated /
     * SentenceFeedback*3 등 여러 record 가 흐름. 본 listener 는 type 식별 없이 모든 메시지 String 수신 후
     * DiaryDeleted 로 readValue 시도 → 다른 type 메시지는 deserialize 실패 → null → ack skip
     * (다른 listener / sub-domain 이 처리).
     *
     * <p><b>Ack 정책</b>: cascade 트랜잭션 commit 후에만 ack. 예외 발생 시 Spring Kafka
     * {@code DefaultErrorHandler} 가 retry — transient DB 장애가 cascade 영구 누락으로 전환되는 anti-pattern 회피.
     */
    @KafkaListener(
        topics = KafkaTopics.DIARY_EVENTS,
        groupId = "${spring.kafka.consumer.group-id}.diary-deleted-sentence-feedback"
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
            ack.acknowledge();  // type 헤더는 일치하나 payload 변형/다른 record — skip
            return;
        }
        handle(event);
        ack.acknowledge();
    }

    private DiaryDeleted deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, DiaryDeleted.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            // diary-events 토픽의 다른 type 메시지가 본 listener 에 도달 — log debug 만, 다른 listener 가 처리.
            log.debug("SentenceFeedbackOnDiaryDeletedListener skipping non-DiaryDeleted payload: {}",
                ex.getMessage());
            return null;
        }
    }

    private void handle(DiaryDeleted event) {
        if (processedEventRepository.existsByConsumerIdAndEventId(CONSUMER_ID, event.eventId())) {
            return;
        }
        UUID diaryId = UUID.fromString(event.diaryId());
        int deleted = sentenceFeedbackRepository.deleteAllByDiaryId(diaryId);
        processedEventRepository.save(
            new ProcessedEventJpaEntity(CONSUMER_ID, event.eventId(), Instant.now(clock))
        );
        log.info("sentence-feedback cascade by DiaryDeleted diaryId={} deleted={}", diaryId, deleted);
    }
}
