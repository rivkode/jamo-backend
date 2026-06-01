package app.backend.jamo.diary.application.dto.diarychat;

import app.backend.jamo.diary.domain.model.diarychat.RoomId;

import java.util.UUID;

/**
 * diarychat room/participant Use Case 의 Command / Query 모음 (S2-a 6 endpoint).
 */
public final class ChatRoomCommands {

    private ChatRoomCommands() {
    }

    /** POST /diary-chatrooms — 일기당 1방 멱등 생성/조회. */
    public record CreateOrGet(UUID diaryId, UUID requesterUserId, boolean aiAssistantEnabled) {
    }

    /** GET /diary-chatrooms/{roomId}. */
    public record Get(RoomId roomId, UUID requesterUserId) {
    }

    /** POST /{roomId}/join — 참여자 등록 (멱등). */
    public record Join(RoomId roomId, UUID userId) {
    }

    /** POST /{roomId}/leave — 참여 해제 (멱등). */
    public record Leave(RoomId roomId, UUID userId) {
    }

    /** POST /{roomId}/ai-toggle — host 만 (비호스트 403). */
    public record SetAiAssistant(RoomId roomId, UUID actorUserId, boolean enabled) {
    }

    /** GET /{roomId}/participants. */
    public record ListParticipants(RoomId roomId, UUID requesterUserId) {
    }
}
