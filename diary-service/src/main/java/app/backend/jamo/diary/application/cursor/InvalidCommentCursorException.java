package app.backend.jamo.diary.application.cursor;

/**
 * 댓글 cursor 디코딩 실패 — base64 / format / parsing 실패.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §6.
 *
 * <p>Presentation 매핑: HTTP 400 (Bad Request).
 */
public class InvalidCommentCursorException extends RuntimeException {
    public InvalidCommentCursorException(String message) {
        super(message);
    }

    public InvalidCommentCursorException(String message, Throwable cause) {
        super(message, cause);
    }
}
