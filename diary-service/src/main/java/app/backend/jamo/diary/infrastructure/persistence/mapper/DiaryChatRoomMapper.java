package app.backend.jamo.diary.infrastructure.persistence.mapper;

import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.infrastructure.persistence.entity.DiaryChatRoomJpaEntity;

/**
 * DiaryChatRoom 도메인 ↔ JpaEntity 변환. 신규(id null)는 auto-increment 위임.
 */
public final class DiaryChatRoomMapper {

    private DiaryChatRoomMapper() {
    }

    public static DiaryChatRoomJpaEntity toJpaEntity(DiaryChatRoom room) {
        Long id = (room.id() == null) ? null : room.id().value();
        return new DiaryChatRoomJpaEntity(
            id,
            room.diaryId(),
            room.hostUserId(),
            room.aiAssistantEnabled(),
            room.createdAt(),
            room.deletedAt()
        );
    }

    public static DiaryChatRoom toDomain(DiaryChatRoomJpaEntity entity) {
        return DiaryChatRoom.reconstitute(
            RoomId.of(entity.getId()),
            entity.getDiaryId(),
            entity.getHostUserId(),
            entity.isAiAssistantEnabled(),
            entity.getCreatedAt(),
            entity.getDeletedAt()
        );
    }
}
