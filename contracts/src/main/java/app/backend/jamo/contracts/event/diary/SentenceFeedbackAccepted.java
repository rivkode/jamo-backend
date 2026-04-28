package app.backend.jamo.contracts.event.diary;

import app.backend.jamo.contracts.event.EventFields;

import java.time.Instant;

/**
 * 사용자가 AI 가 제안한 문장 대안 중 하나를 채택했을 때 diary-service 가 발행하는 도메인 이벤트.
 *
 * <p>발행자: diary-service (SentenceFeedback Aggregate SUGGESTED → ACCEPTED 전이 트랜잭션과 동일
 * Outbox 트랜잭션).
 *
 * <p>구독자:
 * <ul>
 *   <li>platform-service (수락 가중 점수 가산 — 단순 요청보다 활동 가치 큼)</li>
 *   <li>학습 분석 (어떤 제안 패턴이 채택되는지 — 후속 분석 PR)</li>
 * </ul>
 *
 * <p>토픽: {@code diary-events}
 *
 * <p>관련 결정: [decisions/diary/sentence-feedback-domain-policy.md §12].
 *
 * @param eventId       멱등성 키 (UUID 문자열).
 * @param occurredAt    채택 시각.
 * @param feedbackId    SentenceFeedback Aggregate ID (UUID 문자열).
 * @param userId        채택한 사용자 ID (= 요청자, UUID 문자열).
 * @param suggestionId  채택된 제안 ID (UUID 문자열, AI 응답 시 부여된 식별자 중 하나 —
 *                      [decisions/diary/sentence-feedback-domain-policy.md §1, §8]).
 */
public record SentenceFeedbackAccepted(
    String eventId,
    Instant occurredAt,
    String feedbackId,
    String userId,
    String suggestionId
) {
    public SentenceFeedbackAccepted {
        EventFields.requireNonBlank(eventId, "eventId");
        EventFields.requireNonNull(occurredAt, "occurredAt");
        EventFields.requireNonBlank(feedbackId, "feedbackId");
        EventFields.requireNonBlank(userId, "userId");
        EventFields.requireNonBlank(suggestionId, "suggestionId");
    }
}
