package app.backend.jamo.contracts.event.activity;

import app.backend.jamo.contracts.event.EventFields;

import java.time.Instant;

/**
 * 사용자 활동 발생을 알리는 통합 이벤트 — platform-service 의 활동 랭킹 ZSET 가산 트리거.
 *
 * <p>발행자: diary-service, chat-service, comment (diary 내부), learning-service (활성화 시).
 * 각자 도메인 활동 (DiaryCreated 등) 후 본 통합 이벤트도 함께 Outbox 발행하여 platform 만 받게 한다
 * (도메인별 이벤트는 같은 도메인 분석용, 본 이벤트는 platform 표준 진입점).
 *
 * <p>구독자: platform-service (Redis ZSET ranking:global ZINCRBY).
 *
 * <p>토픽: {@code activity-events}
 *
 * <p>{@code points} 정책 (가산 점수) 은 발행자 측 도메인이 결정 — platform 은 그대로 ZINCRBY.
 * 보상 (취소/삭제) 시 음수 points 로 다시 발행 (예: DiaryDeleted → ActivityHappened with negative points).
 *
 * @param eventId    멱등성 키 (UUID 문자열). 구독자의 ProcessedEvent 검증.
 * @param occurredAt 활동 발생 시각.
 * @param userId     활동 주체 사용자 ID.
 * @param type       활동 종류 식별자. 예: {@code "diary.created"} / {@code "comment.created"} /
 *                   {@code "chat.generated"} / {@code "voice.processed"}. 형식 자유 (string) —
 *                   `finish_reason` 과 동일 이유로 enum 미사용 (신규 활동 종류 추가에 호환).
 * @param points     가산 점수 (음수 가능 — 활동 취소/보상). platform 측에서 ZINCRBY 그대로 적용.
 */
public record ActivityHappened(
    String eventId,
    Instant occurredAt,
    String userId,
    String type,
    long points
) {
    public ActivityHappened {
        EventFields.requireNonBlank(eventId, "eventId");
        EventFields.requireNonNull(occurredAt, "occurredAt");
        EventFields.requireNonBlank(userId, "userId");
        EventFields.requireNonBlank(type, "type");
    }
}
