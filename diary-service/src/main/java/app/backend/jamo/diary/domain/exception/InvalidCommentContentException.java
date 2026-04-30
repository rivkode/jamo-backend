package app.backend.jamo.diary.domain.exception;

/**
 * CommentContent VO invariant 위반 — 길이 (1..500 code points) 또는 blank.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md (CommentContent 500cp, 사용자 결정).
 *
 * <p>Presentation 매핑: HTTP 400.
 */
public class InvalidCommentContentException extends RuntimeException {
    public InvalidCommentContentException(String message) {
        super(message);
    }
}
