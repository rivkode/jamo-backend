package app.backend.jamo.diary.application.dto.comment;

import java.util.Objects;
import java.util.UUID;

/**
 * 댓글 좋아요 토글 use case 입력.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §8 (boolean 명시 멱등 — retry 안전).
 *
 * <p>자기 댓글 좋아요 허용 (별도 검증 없음). 비공개 일기 가드는 Application Service 가 Diary 사전 조회 후 수행.
 *
 * @param commentId 대상 댓글
 * @param viewerId  호출자 (좋아요 누르는 사용자)
 * @param liked     true = 좋아요 추가 (멱등), false = 좋아요 취소 (멱등)
 */
public record ToggleCommentLikeCommand(UUID commentId, UUID viewerId, boolean liked) {

    public ToggleCommentLikeCommand {
        Objects.requireNonNull(commentId, "commentId");
        Objects.requireNonNull(viewerId, "viewerId");
    }
}
