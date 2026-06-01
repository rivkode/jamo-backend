package app.backend.jamo.diary.presentation.dto.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.MessageListView;

import java.util.List;

/**
 * GET /api/v1/diary-chatrooms/{roomId}/messages 응답 (API_SPEC E2.7): {items, hasMore, oldestMessageId}.
 */
public record MessageListResponse(List<MessageResponse> items, boolean hasMore, Long oldestMessageId) {

    public static MessageListResponse from(MessageListView v) {
        return new MessageListResponse(
            v.items().stream().map(MessageResponse::from).toList(),
            v.hasMore(),
            v.oldestMessageId());
    }
}
