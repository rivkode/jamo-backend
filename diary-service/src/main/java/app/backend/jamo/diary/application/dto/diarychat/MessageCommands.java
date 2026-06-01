package app.backend.jamo.diary.application.dto.diarychat;

import app.backend.jamo.diary.domain.model.diarychat.RoomId;

import java.util.UUID;

/**
 * diarychat 메시지/롱폴 Use Case 의 Command / Query (S2-b).
 */
public final class MessageCommands {

    private MessageCommands() {
    }

    /** POST /{roomId}/messages — text 필수, audioUrl optional. */
    public record Send(RoomId roomId, UUID senderUserId, String text, String audioUrl) {
    }

    /** GET /{roomId}/messages — before(null=최신부터), size. */
    public record ListMessages(RoomId roomId, UUID requesterUserId, Long before, int size) {
    }

    /** GET /{roomId}/messages/poll — after, wait(초). */
    public record Poll(RoomId roomId, UUID requesterUserId, long after, int waitSeconds) {
    }
}
