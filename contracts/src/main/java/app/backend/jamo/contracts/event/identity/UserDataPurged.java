package app.backend.jamo.contracts.event.identity;

import app.backend.jamo.contracts.event.EventFields;

import java.time.Instant;

/**
 * 회원 탈퇴 Saga 의 회신 이벤트 — 각 서비스가 사용자 데이터 삭제 완료 후 발행.
 *
 * <p>발행자: diary-service, chat-service, learning-service, platform-service
 * (각자의 Outbox 에서 발행).
 *
 * <p>구독자: identity-service. 모든 {@code sourceService} 의 회신을 수신하면 User HARD DELETE.
 *
 * <p>토픽: {@code user-events}
 *
 * <p>{@link UserWithdrawalRequested} 와 짝. 자세한 Saga 시나리오는 module-boundary §6.
 *
 * @param eventId       멱등성 키 (UUID 문자열).
 * @param occurredAt    삭제 완료 시각.
 * @param userId        삭제 대상 사용자 ID.
 * @param sourceService 삭제를 완료한 발행 서비스 식별자. 예: {@code "diary"} / {@code "chat"} /
 *                      {@code "learning"} / {@code "platform"}. string 으로 두어 신규 서비스
 *                      추가 시 contracts breaking change 회피.
 */
public record UserDataPurged(
    String eventId,
    Instant occurredAt,
    String userId,
    String sourceService
) {
    public UserDataPurged {
        EventFields.requireNonBlank(eventId, "eventId");
        EventFields.requireNonNull(occurredAt, "occurredAt");
        EventFields.requireNonBlank(userId, "userId");
        EventFields.requireNonBlank(sourceService, "sourceService");
    }
}
