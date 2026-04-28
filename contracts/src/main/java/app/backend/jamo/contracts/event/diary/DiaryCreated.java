package app.backend.jamo.contracts.event.diary;

import app.backend.jamo.contracts.event.EventFields;

import java.time.Instant;

/**
 * 일기가 작성되었을 때 diary-service 가 발행하는 도메인 이벤트.
 *
 * <p>발행자: diary-service (Diary Aggregate persist 트랜잭션과 동일 Outbox 트랜잭션).
 *
 * <p>구독자: platform-service (랭킹 점수 가산 — 본 이벤트는 도메인 분석용,
 * 표준 활동 진입은 {@link app.backend.jamo.contracts.event.activity.ActivityHappened}).
 *
 * <p>토픽: {@code diary-events}
 *
 * <p>일기 삭제 시 별도 {@code DiaryDeleted} 이벤트 발행 (본 PR 범위 외 — diary 도메인 PR 시점에 추가).
 *
 * @param eventId    멱등성 키 (UUID 문자열).
 * @param occurredAt 일기 작성 시각.
 * @param diaryId    작성된 일기 ID (UUID 문자열).
 * @param userId     작성자 사용자 ID.
 */
public record DiaryCreated(
    String eventId,
    Instant occurredAt,
    String diaryId,
    String userId
) {
    public DiaryCreated {
        EventFields.requireNonBlank(eventId, "eventId");
        EventFields.requireNonNull(occurredAt, "occurredAt");
        EventFields.requireNonBlank(diaryId, "diaryId");
        EventFields.requireNonBlank(userId, "userId");
    }
}
