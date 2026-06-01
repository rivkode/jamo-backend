package app.backend.jamo.diary.infrastructure.persistence.mapper;

import app.backend.jamo.diary.domain.model.diarychat.ChatParticipant;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.infrastructure.persistence.entity.ChatParticipantJpaEntity;

/**
 * ChatParticipant 도메인 ↔ JpaEntity 변환. surrogate id 는 도메인에 없으므로 신규 저장 시 null(auto-gen).
 */
public final class ChatParticipantMapper {

    private ChatParticipantMapper() {
    }

    public static ChatParticipantJpaEntity toJpaEntity(ChatParticipant participant) {
        return new ChatParticipantJpaEntity(
            null,
            participant.roomId().value(),
            participant.userId(),
            participant.joinedAt()
        );
    }

    public static ChatParticipant toDomain(ChatParticipantJpaEntity entity) {
        return ChatParticipant.reconstitute(
            RoomId.of(entity.getRoomId()),
            entity.getUserId(),
            entity.getJoinedAt()
        );
    }
}
