package app.backend.jamo.diary.infrastructure.messaging;

import app.backend.jamo.contracts.event.identity.UserDataPurged;
import app.backend.jamo.contracts.event.identity.UserWithdrawalRequested;
import app.backend.jamo.diary.domain.repository.OutboxEventPublisher;
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
 * 회원 탈퇴 Saga consumer — 박제 §14 (UserWithdrawalRequested 구독 → 사용자 데이터 hard-delete →
 * UserDataPurged 회신 발행).
 *
 * <p>contracts 흐름 (UserWithdrawalRequested / UserDataPurged JavaDoc):
 * <ol>
 *   <li>identity-service: User WITHDRAWING 전이 → {@link UserWithdrawalRequested} 발행</li>
 *   <li>diary/chat/learning/platform 4 서비스 구독 → 자기 도메인 데이터 일괄 삭제</li>
 *   <li>완료 후 {@link UserDataPurged} ({@code sourceService="diary"}) 회신 발행</li>
 *   <li>identity-service 가 모든 회신 수신 시 User HARD DELETE</li>
 * </ol>
 *
 * <p>본 listener 는 sentence_feedback row 만 cascade — 다른 sub-domain 은 후속 슬라이스 별 listener 에서
 * 처리 (decisions §4 — 다른 listener 추가 시 통합 발행 정책 결정).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserWithdrawalRequestedListener {

    static final String CONSUMER_ID = "diary-service.sentence-feedback.UserWithdrawalRequestedListener";
    static final String SOURCE_SERVICE = "diary";

    private final SentenceFeedbackRepository sentenceFeedbackRepository;
    private final SpringDataProcessedEventRepository processedEventRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * <p><b>Ack 정책 (security-reviewer H-1)</b>: cascade + 회신 발행 commit 후에만 ack. 예외 시 Spring Kafka
     * {@code DefaultErrorHandler} retry. user-events 토픽의 다른 type (UserDataPurged 회신 본인 발행분 등) 은
     * deserialize 실패 → ack skip.
     */
    @KafkaListener(
        topics = KafkaTopics.USER_EVENTS,
        groupId = "${spring.kafka.consumer.group-id}.user-withdrawal-sentence-feedback"
    )
    @Transactional
    public void onMessage(String payload, Acknowledgment ack) {
        UserWithdrawalRequested event = deserialize(payload);
        if (event == null) {
            ack.acknowledge();
            return;
        }
        handle(event);
        ack.acknowledge();
    }

    private UserWithdrawalRequested deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, UserWithdrawalRequested.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            log.debug("UserWithdrawalRequestedListener skipping non-matching payload: {}", ex.getMessage());
            return null;
        }
    }

    private void handle(UserWithdrawalRequested event) {
        if (processedEventRepository.existsByConsumerIdAndEventId(CONSUMER_ID, event.eventId())) {
            return;
        }
        UUID userId = UUID.fromString(event.userId());
        int deleted = sentenceFeedbackRepository.deleteAllByUserId(userId);
        processedEventRepository.save(
            new ProcessedEventJpaEntity(CONSUMER_ID, event.eventId(), Instant.now(clock))
        );
        outboxEventPublisher.publish(new UserDataPurged(
            UUID.randomUUID().toString(),
            Instant.now(clock),
            event.userId(),
            SOURCE_SERVICE
        ));
        log.info("UserWithdrawalRequested cascade userId={} deleted={} → UserDataPurged published",
            userId, deleted);
    }
}
