package app.backend.jamo.contracts.event.identity;

import app.backend.jamo.contracts.event.EventFields;

import java.time.Instant;

/**
 * 사용자가 회원 탈퇴를 요청했을 때 identity-service 가 발행하는 이벤트 — 회원 탈퇴 Saga 시작.
 *
 * <p>발행자: identity-service (User 상태 = WITHDRAWING 으로 전이 후 Outbox 발행).
 *
 * <p>구독자: diary-service, chat-service, learning-service, platform-service.
 * 각자 사용자 데이터 일괄 삭제 (diary / comment / chat 메시지 / sentence / shorts / event /
 * 랭킹 ZSET 점수 등) 후 {@link UserDataPurged} 로 회신.
 *
 * <p>토픽: {@code user-events}
 *
 * <p>모든 회신 수신 시 identity-service 가 User HARD DELETE. 일정 시간 미회신 시 운영 알림 +
 * 수동 정리 (보상). 자세한 시나리오는 ADR-0002 + module-boundary §6.
 *
 * @param eventId    멱등성 키 (UUID 문자열).
 * @param occurredAt 탈퇴 요청 시각.
 * @param userId     탈퇴 대상 사용자 ID.
 */
public record UserWithdrawalRequested(
    String eventId,
    Instant occurredAt,
    String userId
) {
    public UserWithdrawalRequested {
        EventFields.requireNonBlank(eventId, "eventId");
        EventFields.requireNonNull(occurredAt, "occurredAt");
        EventFields.requireNonBlank(userId, "userId");
    }
}
