package app.backend.jamo.diary.infrastructure.messaging;

import app.backend.jamo.contracts.event.diary.DiaryCreated;
import app.backend.jamo.contracts.event.diary.DiaryDeleted;
import app.backend.jamo.contracts.event.diary.SentenceFeedbackAccepted;
import app.backend.jamo.contracts.event.diary.SentenceFeedbackRejected;
import app.backend.jamo.contracts.event.diary.SentenceFeedbackRequested;
import app.backend.jamo.contracts.event.identity.UserDataPurged;
import app.backend.jamo.diary.domain.repository.OutboxEventPublisher;
import app.backend.jamo.diary.infrastructure.persistence.entity.OutboxEventJpaEntity;
import app.backend.jamo.diary.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * OutboxEventPublisher port 구현. domain 트랜잭션 안에서 row insert (Kafka 발행은 {@link OutboxPoller}).
 *
 * <p>지원 record 6종 (현 PR 시점):
 * <ul>
 *   <li>{@link DiaryCreated}              → topic {@code diary-events} (aggregate=diary)</li>
 *   <li>{@link DiaryDeleted}              → topic {@code diary-events} (aggregate=diary)</li>
 *   <li>{@link SentenceFeedbackRequested} → topic {@code diary-events} (aggregate=sentence_feedback)</li>
 *   <li>{@link SentenceFeedbackAccepted}  → topic {@code diary-events}</li>
 *   <li>{@link SentenceFeedbackRejected}  → topic {@code diary-events}</li>
 *   <li>{@link UserDataPurged}            → topic {@code user-events} (aggregate=user)</li>
 * </ul>
 *
 * <p>각 record 의 {@code eventId} / {@code aggregateId} 추출은 record class 별 명시 분기 — 새 record
 * 추가 시 본 publisher 갱신 필요. 후속 marker interface ({@code DomainEventEnvelope}) 도입 검토는
 * decisions/diary/sentence-feedback-infra-decisions.md 박제 (premature abstraction 회피).
 */
@Component
@RequiredArgsConstructor
public class OutboxEventPublisherImpl implements OutboxEventPublisher {

    private final SpringDataOutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public void publish(Object event) {
        OutboxEventJpaEntity entity = build(event);
        repository.save(entity);
    }

    private OutboxEventJpaEntity build(Object event) {
        Instant now = Instant.now(clock);
        String payload = serialize(event);

        if (event instanceof DiaryCreated e) {
            return new OutboxEventJpaEntity(
                e.eventId(), "diary", e.diaryId(),
                DiaryCreated.class.getName(), KafkaTopics.DIARY_EVENTS, payload, now
            );
        }
        if (event instanceof DiaryDeleted e) {
            return new OutboxEventJpaEntity(
                e.eventId(), "diary", e.diaryId(),
                DiaryDeleted.class.getName(), KafkaTopics.DIARY_EVENTS, payload, now
            );
        }
        if (event instanceof SentenceFeedbackRequested e) {
            return new OutboxEventJpaEntity(
                e.eventId(), "sentence_feedback", e.feedbackId(),
                SentenceFeedbackRequested.class.getName(), KafkaTopics.DIARY_EVENTS, payload, now
            );
        }
        if (event instanceof SentenceFeedbackAccepted e) {
            return new OutboxEventJpaEntity(
                e.eventId(), "sentence_feedback", e.feedbackId(),
                SentenceFeedbackAccepted.class.getName(), KafkaTopics.DIARY_EVENTS, payload, now
            );
        }
        if (event instanceof SentenceFeedbackRejected e) {
            return new OutboxEventJpaEntity(
                e.eventId(), "sentence_feedback", e.feedbackId(),
                SentenceFeedbackRejected.class.getName(), KafkaTopics.DIARY_EVENTS, payload, now
            );
        }
        if (event instanceof UserDataPurged e) {
            return new OutboxEventJpaEntity(
                e.eventId(), "user", e.userId(),
                UserDataPurged.class.getName(), KafkaTopics.USER_EVENTS, payload, now
            );
        }
        throw new IllegalArgumentException("unsupported outbox event type: " + event.getClass().getName());
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize outbox event: " + event.getClass().getName(), e);
        }
    }
}
