package app.backend.jamo.contracts.event.diary;

import app.backend.jamo.contracts.event.EventFields;

import java.time.Instant;

/**
 * 사용자가 AI 의 모든 문장 제안을 거부했을 때 diary-service 가 발행하는 도메인 이벤트.
 *
 * <p>발행자: diary-service (SentenceFeedback Aggregate SUGGESTED → REJECTED 전이 트랜잭션과 동일
 * Outbox 트랜잭션).
 *
 * <p>구독자: 학습 분석 (제안 품질 개선 신호 — 후속 분석 PR. 본 시점 platform-service 점수 가산 정책 미적용 또는
 * 매우 작게, [decisions/diary/sentence-feedback-domain-policy.md §12]).
 *
 * <p>토픽: {@code diary-events}
 *
 * <p>발행 채택 근거: 라이프사이클 final 전이 3종 (Accepted / Rejected / Expired 중 본 시점 Accepted/Rejected) 통일성
 * + 학습 신호 가치. 발행 비용 (Outbox row + Kafka) 대비 분석 가치 큼.
 * PRD `rejectSentenceFeedback.md §8` Open Item 해소 — 발행 채택.
 *
 * <p>{@code reason} 필드는 자유 텍스트로 학습 분석 가치가 있으나, 본 record 에서는 단순화 위해 제외 —
 * 학습 분석은 별도 데이터 파이프라인 (DB 직접 조회 또는 후속 호환 변경) 으로 접근.
 *
 * @param eventId    멱등성 키 (UUID 문자열).
 * @param occurredAt 거부 시각.
 * @param feedbackId SentenceFeedback Aggregate ID (UUID 문자열).
 * @param userId     거부한 사용자 ID (UUID 문자열).
 */
public record SentenceFeedbackRejected(
    String eventId,
    Instant occurredAt,
    String feedbackId,
    String userId
) {
    public SentenceFeedbackRejected {
        EventFields.requireNonBlank(eventId, "eventId");
        EventFields.requireNonNull(occurredAt, "occurredAt");
        EventFields.requireNonBlank(feedbackId, "feedbackId");
        EventFields.requireNonBlank(userId, "userId");
    }
}
