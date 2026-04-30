package app.backend.jamo.diary.domain.exception;

/**
 * 답글 깊이 1단 제한 invariant 위반 — 답글에 답글 시도 ({@code parent.parentId != null} 인 댓글에 답글 작성).
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §2 (답글 깊이 1단 제한, parent_must_be_root).
 *
 * <p><b>발생 위치</b>: Application Service ({@code CreateCommentService}) — Aggregate 가 다른 Aggregate 상태
 * (parent.parentId) 를 알지 않도록 분리 (사용자 결정 + ddd-architect Q2 정합). 단 타입 정의는 {@code domain/exception/}
 * 에 위치 — 다른 Application Service 가 catch 가능 (sentence-feedback {@code InvalidTransition} 정합).
 *
 * <p>Presentation 매핑: HTTP 400.
 */
public class InvalidCommentParentException extends RuntimeException {
    public InvalidCommentParentException(String message) {
        super(message);
    }
}
