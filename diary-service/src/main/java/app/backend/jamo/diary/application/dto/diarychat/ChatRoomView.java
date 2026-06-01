package app.backend.jamo.diary.application.dto.diarychat;

import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;

import java.time.Instant;
import java.util.UUID;

/**
 * 채팅방 조회 결과 (API_SPEC 부록 E.2 DiaryChatRoom).
 */
public record ChatRoomView(
    long roomId,
    UUID diaryId,
    UUID hostUserId,
    boolean aiAssistantEnabled,
    long participantCount,
    Instant createdAt
) {
    public static ChatRoomView of(DiaryChatRoom room, long participantCount) {
        return new ChatRoomView(
            room.id().value(),
            room.diaryId(),
            room.hostUserId(),
            room.aiAssistantEnabled(),
            participantCount,
            room.createdAt()
        );
    }
}
