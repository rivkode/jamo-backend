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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * DiaryDeleted Saga consumer — 박제 §13 (sentence-feedback 자체 cascade).
 *
 * <p>diary-service 가 자체 발행한 {@link DiaryDeleted} 를 자체 구독해 sentence_feedback 중 같은 diary 의
 * row 만 hard-delete. {@code diary_id NULL} row 는 영향 X (작성 전 미리보기 — diary 결합 X).
 *
 * <p><b>Wire format</b>: producer 가 raw JSON String 발행 (OutboxEventPublisherImpl + OutboxPublisherTx),
 * consumer 가 String 수신 후 listener 메서드에서 ObjectMapper 로 record 변환 (code-reviewer C1 — type
 * header 의존 회피, sub-domain 별 명시 deserialize).
 *
 * <p>멱등성: {@code (consumer_id, event_id)} 가 ProcessedEvent 에 이미 있으면 skip. 트랜잭션 안에서
 * cascade + ProcessedEvent insert → 부분 실패 시 재시도 안전.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiaryDeletedListener {

    static final String CONSUMER_ID = "diary-service.sentence-feedback.DiaryDeletedListener";

    private final SentenceFeedbackRepository sentenceFeedbackRepository;
    private final SpringDataProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * <p><b>Topic 다형성</b>: {@code diary-events} 토픽은 DiaryDeleted 외 DiaryCreated / CommentCreated /
     * SentenceFeedback*3 등 여러 record 가 흐름. 본 listener 는 type 식별 없이 모든 메시지 String 수신 후
     * DiaryDeleted 로 readValue 시도 → 다른 type 메시지는 IOException 또는 mismatched field 로 실패하므로
     * 후속 PR 에서 type 식별 필드 (예: payload 의 `type` 필드 또는 Kafka header) 로 분기 필요. 본 PR
     * 시점은 diary-events 토픽에 DiaryDeleted 만 발행되는 단일 sub-domain 단계.
     *
     * <p><b>Ack 정책 (security-reviewer H-1)</b>: cascade 트랜잭션 commit 후에만 ack. 예외 발생 시
     * Spring Kafka {@code DefaultErrorHandler} 가 retry — transient DB 장애가 GDPR cascade 영구 누락
     * 으로 전환되는 anti-pattern 회피.
     */
    @KafkaListener(
        topics = KafkaTopics.DIARY_EVENTS,
        groupId = "${spring.kafka.consumer.group-id}.diary-deleted-sentence-feedback"
    )
    @Transactional
    public void onMessage(String payload, Acknowledgment ack) {
        DiaryDeleted event = deserialize(payload);
        if (event == null) {
            ack.acknowledge();  // 본 listener 가 처리할 수 없는 type — skip (다른 listener 가 처리)
            return;
        }
        handle(event);
        ack.acknowledge();
    }

    private DiaryDeleted deserialize(String payload) {
        try {
            DiaryDeleted event = objectMapper.readValue(payload, DiaryDeleted.class);
            // 필수 필드 보강 검증 — record compact constructor 가 throw 시 catch 못 함 (이미 위에서 throw)
            return event;
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            // diary-events 토픽의 다른 type 메시지 (DiaryCreated / SentenceFeedback*3 등) 가 본 listener 에 도달.
            // log debug 만 — 정상 흐름 (다른 listener / 후속 sub-domain 이 처리).
            log.debug("DiaryDeletedListener skipping non-DiaryDeleted payload: {}", ex.getMessage());
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
        log.info("DiaryDeleted cascade diaryId={} deleted={}", diaryId, deleted);
    }
}
