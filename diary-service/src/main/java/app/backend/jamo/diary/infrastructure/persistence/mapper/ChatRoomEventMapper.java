package app.backend.jamo.diary.infrastructure.persistence.mapper;

import app.backend.jamo.diary.domain.model.diarychat.ChatRoomEvent;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.infrastructure.persistence.entity.ChatRoomEventJpaEntity;

/**
 * ChatRoomEvent 도메인 ↔ JpaEntity 변환.
 */
public final class ChatRoomEventMapper {

    private ChatRoomEventMapper() {
    }

    public static ChatRoomEventJpaEntity toJpaEntity(ChatRoomEvent e) {
        return new ChatRoomEventJpaEntity(
            e.eventId(),
            e.roomId().value(),
            e.type(),
            e.actorUserId(),
            e.enabled().orElse(null),
            e.createdAt()
        );
    }

    public static ChatRoomEvent toDomain(ChatRoomEventJpaEntity e) {
        return ChatRoomEvent.reconstitute(
            e.getId(),
            RoomId.of(e.getRoomId()),
            e.getType(),
            e.getActorUserId(),
            e.getEnabled(),
            e.getCreatedAt()
        );
    }
}
