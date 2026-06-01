package app.backend.jamo.diary.presentation.dto.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.PollView;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/v1/diary-chatrooms/{roomId}/messages/poll 응답 (API_SPEC E2.8): {items, events, nextAfter}.
 */
public record PollResponse(List<MessageResponse> items, List<EventItem> events, long nextAfter) {

    /** DiaryChatEvent: {type, at, userId, enabled?}. type/enabled 는 wire 형식. */
    public record EventItem(String type, Instant at, String userId, Boolean enabled) {
    }

    public static PollResponse from(PollView v) {
        return new PollResponse(
            v.items().stream().map(MessageResponse::from).toList(),
            v.events().stream()
                .map(e -> new EventItem(
                    e.type().wireValue(),
                    e.at(),
                    e.userId() == null ? null : e.userId().toString(),
                    e.enabled()))
                .toList(),
            v.nextAfter());
    }
}
