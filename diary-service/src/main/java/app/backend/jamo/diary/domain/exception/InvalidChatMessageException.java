package app.backend.jamo.diary.domain.exception;

/**
 * 채팅 메시지 입력이 도메인 불변식을 위반할 때 (빈 text / 길이 초과 / 잘못된 audioUrl). presentation 400.
 */
public class InvalidChatMessageException extends RuntimeException {

    public InvalidChatMessageException(String message) {
        super(message);
    }
}
