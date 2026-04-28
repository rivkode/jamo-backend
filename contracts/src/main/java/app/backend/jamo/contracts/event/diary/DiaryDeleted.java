package app.backend.jamo.contracts.event.diary;

import app.backend.jamo.contracts.event.EventFields;

import java.time.Instant;

/**
 * 일기가 삭제되었을 때 diary-service 가 발행하는 도메인 이벤트.
 *
 * <p>발행자: diary-service (Diary hard-delete 트랜잭션과 동일 Outbox 트랜잭션 — 분산 트랜잭션 / 2PC 미사용,
 * Saga + 보상 트랜잭션 패턴, [decisions/diary/diary-domain-policy.md §9]).
 *
 * <p>구독자:
 * <ul>
 *   <li>diary-service 자체 (comments / diary_likes / sentence_feedback hard-delete cascade —
 *       [decisions/diary/diary-domain-policy.md §10] / [sentence-feedback-domain-policy.md §13])</li>
 *   <li>chat-service / diary-service 의 diarychat 영역 (chatrooms <b>soft-delete</b> {@code deleted_at} 채움,
 *       메시지 보존 — [decisions/diary/diarychat-domain-policy.md §16])</li>
 *   <li>platform-service (랭킹 점수 차감 또는 보상 ActivityHappened 발행)</li>
 * </ul>
 *
 * <p>토픽: {@code diary-events}
 *
 * <p>모든 구독자는 {@code ProcessedEvent} 테이블로 멱등성 처리 필수
 * (CLAUDE.md Kafka Consumer 멱등 의무).
 *
 * @param eventId    멱등성 키 (UUID 문자열).
 * @param occurredAt 일기 삭제 시각.
 * @param diaryId    삭제된 일기 ID (UUID 문자열).
 * @param userId     일기 작성자 사용자 ID (UUID 문자열).
 */
public record DiaryDeleted(
    String eventId,
    Instant occurredAt,
    String diaryId,
    String userId
) {
    public DiaryDeleted {
        EventFields.requireNonBlank(eventId, "eventId");
        EventFields.requireNonNull(occurredAt, "occurredAt");
        EventFields.requireNonBlank(diaryId, "diaryId");
        EventFields.requireNonBlank(userId, "userId");
    }
}
