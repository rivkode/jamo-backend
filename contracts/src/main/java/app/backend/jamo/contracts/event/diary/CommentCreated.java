package app.backend.jamo.contracts.event.diary;

import app.backend.jamo.contracts.event.EventFields;

import java.time.Instant;

/**
 * 일기 댓글이 작성되었을 때 diary-service 가 발행하는 도메인 이벤트.
 *
 * <p>발행자: diary-service (Comment Aggregate persist 트랜잭션과 동일 Outbox 트랜잭션).
 *
 * <p>구독자: platform-service (랭킹 점수 가산).
 *
 * <p>토픽: {@code diary-events}
 *
 * @param eventId    멱등성 키 (UUID 문자열).
 * @param occurredAt 댓글 작성 시각.
 * @param commentId  작성된 댓글 ID.
 * @param diaryId    댓글 대상 일기 ID.
 * @param userId     작성자 사용자 ID.
 */
public record CommentCreated(
    String eventId,
    Instant occurredAt,
    String commentId,
    String diaryId,
    String userId
) {
    public CommentCreated {
        EventFields.requireNonBlank(eventId, "eventId");
        EventFields.requireNonNull(occurredAt, "occurredAt");
        EventFields.requireNonBlank(commentId, "commentId");
        EventFields.requireNonBlank(diaryId, "diaryId");
        EventFields.requireNonBlank(userId, "userId");
    }
}
