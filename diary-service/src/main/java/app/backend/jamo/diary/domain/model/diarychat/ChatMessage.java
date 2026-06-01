package app.backend.jamo.diary.domain.model.diarychat;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 채팅방 메시지 Aggregate Root (roomId 외래참조). 박제 v2 §8-b.
 *
 * <p>late identity — {@link #userMessage} 신규는 영속 전 id=null, {@code save} 가 채워 반환.
 *
 * <p>불변식: USER 메시지는 authorUserId + text 필수 (STT 는 클라가 처리해 항상 text 보유). audioUrl optional.
 * AI/SYSTEM 메시지(S4)는 authorUserId=null 허용 — 본 슬라이스 범위 밖.
 */
public class ChatMessage {

    private final MessageId id;            // null = 영속 전
    private final RoomId roomId;
    private final UUID authorUserId;       // null = AI/SYSTEM
    private final MessageText text;
    private final MessageAudioUrl audioUrl; // null = 음성 없음
    private final MessageSource source;
    private final Instant createdAt;

    private ChatMessage(MessageId id, RoomId roomId, UUID authorUserId, MessageText text,
                        MessageAudioUrl audioUrl, MessageSource source, Instant createdAt) {
        this.id = id;
        this.roomId = roomId;
        this.authorUserId = authorUserId;
        this.text = text;
        this.audioUrl = audioUrl;
        this.source = source;
        this.createdAt = createdAt;
    }

    /** 사용자 발화 — authorUserId + text 필수, audioUrl optional. */
    public static ChatMessage userMessage(RoomId roomId, UUID authorUserId, MessageText text,
                                          MessageAudioUrl audioUrl, Clock clock) {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(authorUserId, "authorUserId");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(clock, "clock");
        return new ChatMessage(null, roomId, authorUserId, text, audioUrl, MessageSource.USER, clock.instant());
    }

    /** AI 어시스턴트 자동응답 (S4) — authorUserId 없음, text 필수, audioUrl 없음. */
    public static ChatMessage aiMessage(RoomId roomId, MessageText text, Clock clock) {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(clock, "clock");
        return new ChatMessage(null, roomId, null, text, null, MessageSource.AI, clock.instant());
    }

    /** 시스템 안내 메시지 (S4 — AI 생성 실패/한도 통지) — authorUserId 없음, text 필수. */
    public static ChatMessage systemMessage(RoomId roomId, MessageText text, Clock clock) {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(clock, "clock");
        return new ChatMessage(null, roomId, null, text, null, MessageSource.SYSTEM, clock.instant());
    }

    /** persistence 재구성 (Mapper 전용). */
    public static ChatMessage reconstitute(MessageId id, RoomId roomId, UUID authorUserId, MessageText text,
                                           MessageAudioUrl audioUrl, MessageSource source, Instant createdAt) {
        Objects.requireNonNull(id, "id");
        return new ChatMessage(id, roomId, authorUserId, text, audioUrl, source, createdAt);
    }

    public MessageId id() {
        return id;
    }

    public RoomId roomId() {
        return roomId;
    }

    /** AI/SYSTEM 이면 empty. */
    public Optional<UUID> authorUserId() {
        return Optional.ofNullable(authorUserId);
    }

    public String text() {
        return text == null ? null : text.value();
    }

    public Optional<String> audioUrl() {
        return Optional.ofNullable(audioUrl).map(MessageAudioUrl::value);
    }

    public MessageSource source() {
        return source;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
