package app.backend.jamo.contracts.event.diary;

import app.backend.jamo.contracts.event.EventFields;

import java.time.Instant;

/**
 * 사용자가 일기 한 문장 (1..50 code points) 에 대한 AI 피드백을 요청했을 때
 * diary-service 가 발행하는 도메인 이벤트.
 *
 * <p>발행자: diary-service (SentenceFeedback Aggregate persist 트랜잭션과 동일 Outbox 트랜잭션 —
 * status=REQUESTED 또는 SUGGESTED 도달 시점에 발행).
 *
 * <p>구독자: platform-service (활동 점수 가산. 표준 활동 진입은
 * {@link app.backend.jamo.contracts.event.activity.ActivityHappened}).
 *
 * <p>토픽: {@code diary-events}
 *
 * <p>관련 결정: [decisions/diary/sentence-feedback-domain-policy.md §12].
 *
 * <p>본 시점 이벤트는 {@code feedbackId} / {@code userId} 만 보존 — {@code diaryId} 는
 * sentence-feedback 이 일기 저장 전 미리보기 흐름에서 호출 가능하므로 ([sentence-feedback-domain-policy.md §5])
 * 본 이벤트에 강제 포함하지 않음. 후속 학습 분석 / diary 결합이 필요해지면 호환 변경 (record 새 필드 추가, ADR-0004 §6).
 *
 * @param eventId    멱등성 키 (UUID 문자열).
 * @param occurredAt 피드백 요청 시각.
 * @param feedbackId SentenceFeedback Aggregate ID (UUID 문자열).
 * @param userId     요청 사용자 ID (UUID 문자열).
 */
public record SentenceFeedbackRequested(
    String eventId,
    Instant occurredAt,
    String feedbackId,
    String userId
) {
    public SentenceFeedbackRequested {
        EventFields.requireNonBlank(eventId, "eventId");
        EventFields.requireNonNull(occurredAt, "occurredAt");
        EventFields.requireNonBlank(feedbackId, "feedbackId");
        EventFields.requireNonBlank(userId, "userId");
    }
}
