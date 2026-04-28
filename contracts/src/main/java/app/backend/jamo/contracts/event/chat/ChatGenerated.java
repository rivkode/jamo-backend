package app.backend.jamo.contracts.event.chat;

import app.backend.jamo.contracts.event.EventFields;

import java.time.Instant;

/**
 * AI 채팅 응답이 생성되었을 때 chat-service 가 발행하는 도메인 이벤트.
 *
 * <p>발행자: chat-service (Chat Aggregate / 메시지 persist 트랜잭션과 동일 Outbox 트랜잭션).
 *
 * <p>구독자: platform-service (랭킹 점수 가산).
 *
 * <p>토픽: {@code chat-events}
 *
 * <p>{@code roomId} 가 빈 문자열이면 1:1 (개인 chat) — diarychat 등 룸 컨텍스트가 있는 케이스만
 * 채워짐. AI 응답 생성 자체가 활동 단위 (사용자 prompt 1회 = 응답 1회 = 이벤트 1회).
 *
 * @param eventId    멱등성 키 (UUID 문자열).
 * @param occurredAt 응답 생성 시각.
 * @param chatId     생성된 채팅 메시지/세션 ID.
 * @param userId     응답을 요청한 사용자 ID.
 * @param roomId     관련 채팅방 ID (1:1 채팅이면 빈 문자열).
 */
public record ChatGenerated(
    String eventId,
    Instant occurredAt,
    String chatId,
    String userId,
    String roomId
) {
    public ChatGenerated {
        EventFields.requireNonBlank(eventId, "eventId");
        EventFields.requireNonNull(occurredAt, "occurredAt");
        EventFields.requireNonBlank(chatId, "chatId");
        EventFields.requireNonBlank(userId, "userId");
        // roomId 는 1:1 chat 표현을 위해 빈 문자열 허용. null 만 거부 (의도적으로 requireNonNull 사용).
        EventFields.requireNonNull(roomId, "roomId");
    }
}
