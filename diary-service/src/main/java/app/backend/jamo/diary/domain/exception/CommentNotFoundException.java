package app.backend.jamo.diary.domain.exception;

/**
 * Comment Aggregate 부재 시 발생.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §4 (404 통일 IDOR 보호).
 *
 * <p>Presentation 매핑: HTTP 404.
 */
public class CommentNotFoundException extends RuntimeException {
    public CommentNotFoundException(String message) {
        super(message);
    }
}
