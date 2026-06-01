package app.backend.jamo.diary.presentation.dto.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.MessageView;

import java.time.Instant;

/**
 * API_SPEC 부록 E.2 DiaryChatMessage. author 는 {userId,username,avatarUrl}, source 는 wire 소문자.
 */
public record MessageResponse(
    long messageId,
    long roomId,
    Author author,
    String text,
    String audioUrl,
    Instant createdAt,
    String source
) {
    /** AI/SYSTEM 메시지는 userId/username null. */
    public record Author(String userId, String username, String avatarUrl) {
    }

    public static MessageResponse from(MessageView v) {
        Author author = new Author(
            v.authorUserId() == null ? null : v.authorUserId().toString(),
            v.authorDisplayName(),
            null);
        return new MessageResponse(
            v.messageId(), v.roomId(), author, v.text(), v.audioUrl(), v.createdAt(), v.source().wireValue());
    }
}
