package app.backend.jamo.diary.application.dto.diarychat;

import java.util.List;

/**
 * poll 결과 (API_SPEC E2.8) — {@code {items, events, nextAfter}}. {@link #hasData} 면 즉시 반환.
 */
public record PollView(List<MessageView> items, List<ChatEventView> events, long nextAfter) {

    public boolean hasData() {
        return !items.isEmpty() || !events.isEmpty();
    }

    public static PollView empty(long after) {
        return new PollView(List.of(), List.of(), after);
    }
}
