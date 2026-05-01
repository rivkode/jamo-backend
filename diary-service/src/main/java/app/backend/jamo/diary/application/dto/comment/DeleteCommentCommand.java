package app.backend.jamo.diary.application.dto.comment;

import java.util.Objects;
import java.util.UUID;

/**
 * 댓글 삭제 use case 입력.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §3 (hard-delete + cascade 답글) / §4 (404 통일 IDOR).
 *
 * <p>일기 작성자 강제 삭제 권한 미부여 (신고 시스템 후속) — 본 command 는 작성자 본인만 사용.
 *
 * @param commentId   삭제 대상
 * @param requesterId 호출자 (작성자 검증 키)
 */
public record DeleteCommentCommand(UUID commentId, UUID requesterId) {

    public DeleteCommentCommand {
        Objects.requireNonNull(commentId, "commentId");
        Objects.requireNonNull(requesterId, "requesterId");
    }
}
