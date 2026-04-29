package app.backend.jamo.diary.application.cursor;

/**
 * 피드 cursor 디코딩 실패 — base64 / format / sort prefix / parsing 실패.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §7.
 *
 * <p>Presentation 매핑: HTTP 400 (Bad Request).
 */
public class InvalidDiaryFeedCursorException extends RuntimeException {
    public InvalidDiaryFeedCursorException(String message) {
        super(message);
    }

    public InvalidDiaryFeedCursorException(String message, Throwable cause) {
        super(message, cause);
    }
}
