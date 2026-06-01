package app.backend.jamo.diary.domain.repository;

import app.backend.jamo.diary.domain.model.diarychat.ChatMessage;
import app.backend.jamo.diary.domain.model.diarychat.MessageId;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;

import java.util.List;

/**
 * ChatMessage Repository port.
 */
public interface ChatMessageRepository {

    /** 저장 후 생성된 messageId 를 채운 인스턴스 반환 (late identity). */
    ChatMessage save(ChatMessage message);

    /**
     * 과거 페이지 (listMessages) — {@code beforeOrNull} 미만(없으면 최신부터) id 내림차순 limit 건.
     * limit+1 조회로 hasMore 판단은 호출 측(또는 구현)에서.
     */
    List<ChatMessage> findByRoomIdBefore(RoomId roomId, MessageId beforeOrNull, int limit);

    /** 롱폴 — {@code after} 초과 id 오름차순 (새 메시지). limit 으로 폭주 방지. */
    List<ChatMessage> findByRoomIdAfter(RoomId roomId, long after, int limit);
}
