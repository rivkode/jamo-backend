package app.backend.jamo.diary.presentation.dto.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.ChatRoomView;

import java.time.Instant;

/**
 * API_SPEC 부록 E.2 DiaryChatRoom 응답. roomId 는 int64 숫자, diaryId/hostUserId 는 UUID 문자열.
 */
public record ChatRoomResponse(
    long roomId,
    String diaryId,
    String hostUserId,
    boolean aiAssistantEnabled,
    long participantCount,
    Instant createdAt
) {
    public static ChatRoomResponse from(ChatRoomView v) {
        return new ChatRoomResponse(
            v.roomId(),
            v.diaryId().toString(),
            v.hostUserId().toString(),
            v.aiAssistantEnabled(),
            v.participantCount(),
            v.createdAt()
        );
    }
}
