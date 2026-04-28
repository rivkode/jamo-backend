package app.backend.jamo.contracts.event.chat;

import app.backend.jamo.contracts.event.EventFields;

import java.time.Instant;

/**
 * 사용자의 음성 입력이 STT 처리되었을 때 chat-service 가 발행하는 도메인 이벤트.
 *
 * <p>발행자: chat-service (음성 STT 호출 후 Outbox 발행).
 *
 * <p>구독자: platform-service (랭킹 점수 가산).
 *
 * <p>토픽: {@code chat-events}
 *
 * <p>STT 자체는 chat-service → ai-service ({@code AiService.SpeechToText}) gRPC 로 처리되지만,
 * 활동 이벤트 발행은 chat-service 의 트랜잭션 책임 (ai-service 는 무상태).
 *
 * @param eventId    멱등성 키 (UUID 문자열).
 * @param occurredAt STT 처리 완료 시각.
 * @param chatId     음성 입력에 대응되는 채팅 메시지 ID.
 * @param userId     음성 입력 사용자 ID.
 * @param durationMs 음성 길이 (밀리초). 활동 점수 가중치 산정에 사용 가능.
 */
public record VoiceInputProcessed(
    String eventId,
    Instant occurredAt,
    String chatId,
    String userId,
    long durationMs
) {
    public VoiceInputProcessed {
        EventFields.requireNonBlank(eventId, "eventId");
        EventFields.requireNonNull(occurredAt, "occurredAt");
        EventFields.requireNonBlank(chatId, "chatId");
        EventFields.requireNonBlank(userId, "userId");
        EventFields.requireNonNegative(durationMs, "durationMs");
    }
}
