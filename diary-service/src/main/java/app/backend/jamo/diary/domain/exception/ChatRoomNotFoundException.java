package app.backend.jamo.diary.domain.exception;

/**
 * 채팅방 부재 / 비공개 일기 비작성자 / 삭제된 방 — presentation 에서 404 (IDOR 통일).
 */
public class ChatRoomNotFoundException extends RuntimeException {

    public ChatRoomNotFoundException(String message) {
        super(message);
    }
}
