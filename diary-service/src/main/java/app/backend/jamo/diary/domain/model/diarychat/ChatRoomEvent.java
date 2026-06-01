package app.backend.jamo.diary.domain.model.diarychat;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 롱폴 전용 in-room 상태 변화 append 로그 (Kafka 도메인 이벤트 아님 — poll 응답 events 소스). 박제 v2 §8-b.
 *
 * <p>eventId BIGINT auto-increment (poll baseline 커서). join/leave/ai-toggle 시 append. enabled 는
 * AI_TOGGLE_CHANGED 만 채움.
 */
public class ChatRoomEvent {

    private final Long eventId;        // null = 영속 전
    private final RoomId roomId;
    private final ChatRoomEventType type;
    private final UUID actorUserId;
    private final Boolean enabled;     // AI_TOGGLE_CHANGED 만
    private final Instant createdAt;

    private ChatRoomEvent(Long eventId, RoomId roomId, ChatRoomEventType type,
                          UUID actorUserId, Boolean enabled, Instant createdAt) {
        this.eventId = eventId;
        this.roomId = roomId;
        this.type = type;
        this.actorUserId = actorUserId;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    public static ChatRoomEvent participantJoined(RoomId roomId, UUID actor, Clock clock) {
        return new ChatRoomEvent(null, roomId, ChatRoomEventType.PARTICIPANT_JOINED, actor, null, clock.instant());
    }

    public static ChatRoomEvent participantLeft(RoomId roomId, UUID actor, Clock clock) {
        return new ChatRoomEvent(null, roomId, ChatRoomEventType.PARTICIPANT_LEFT, actor, null, clock.instant());
    }

    public static ChatRoomEvent aiToggleChanged(RoomId roomId, UUID actor, boolean enabled, Clock clock) {
        return new ChatRoomEvent(null, roomId, ChatRoomEventType.AI_TOGGLE_CHANGED, actor, enabled, clock.instant());
    }

    public static ChatRoomEvent reconstitute(Long eventId, RoomId roomId, ChatRoomEventType type,
                                             UUID actorUserId, Boolean enabled, Instant createdAt) {
        Objects.requireNonNull(eventId, "eventId");
        return new ChatRoomEvent(eventId, roomId, type, actorUserId, enabled, createdAt);
    }

    public Long eventId() {
        return eventId;
    }

    public RoomId roomId() {
        return roomId;
    }

    public ChatRoomEventType type() {
        return type;
    }

    public UUID actorUserId() {
        return actorUserId;
    }

    public Optional<Boolean> enabled() {
        return Optional.ofNullable(enabled);
    }

    public Instant createdAt() {
        return createdAt;
    }
}
