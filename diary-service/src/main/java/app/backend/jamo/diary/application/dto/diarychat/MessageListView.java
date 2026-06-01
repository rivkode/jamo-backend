package app.backend.jamo.diary.application.dto.diarychat;

import java.util.List;

/**
 * listMessages 결과 (API_SPEC E2.7) — 최근 desc, hasMore, oldestMessageId(다음 before 커서).
 */
public record MessageListView(List<MessageView> items, boolean hasMore, Long oldestMessageId) {
}
