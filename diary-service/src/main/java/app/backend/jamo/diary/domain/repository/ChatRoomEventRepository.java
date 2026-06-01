package app.backend.jamo.diary.domain.repository;

import app.backend.jamo.diary.domain.model.diarychat.ChatRoomEvent;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;

import java.util.List;

/**
 * ChatRoomEvent append 로그 Repository port (롱폴 events 소스).
 */
public interface ChatRoomEventRepository {

    void append(ChatRoomEvent event);

    /** poll baseline — 방의 현재 최대 event_id (없으면 0). */
    long maxEventIdByRoomId(RoomId roomId);

    /** {@code afterEventId} 초과 이벤트 오름차순 (poll 윈도우 동안 발생분). */
    List<ChatRoomEvent> findByRoomIdAfter(RoomId roomId, long afterEventId, int limit);
}
