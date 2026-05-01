package app.backend.jamo.diary.application.dto.comment;

import app.backend.jamo.diary.domain.model.comment.Comment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 댓글 응답 view (9 필드, 박제 §7).
 *
 * <p>create / list 응답 항목 동일 schema. 작성 직후 likeCount=0 / likedByMe=false.
 *
 * <p>{@code authorDisplayName} 은 UserSummary gRPC 응답으로 조립 (외래 BC). NOT_FOUND / fallback 시
 * {@code "(unknown)"} ({@code UserSummaryView.displayNameOrUnknown}).
 *
 * <p>{@code parentId} 는 nullable — null = 루트 댓글, non-null = 1단 답글 (박제 §2). 클라이언트가 parentId 로
 * flat list 를 트리 구성 (박제 §6 — 서버측 트리 구조 미반환).
 *
 * @param parentId nullable (루트 댓글이면 null)
 */
public record CommentView(
    UUID commentId,
    UUID diaryId,
    UUID authorId,
    String authorDisplayName,
    String content,
    UUID parentId,
    int likeCount,
    boolean likedByMe,
    Instant createdAt
) {

    public CommentView {
        Objects.requireNonNull(commentId, "commentId");
        Objects.requireNonNull(diaryId, "diaryId");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(authorDisplayName, "authorDisplayName");
        Objects.requireNonNull(content, "content");
        // parentId nullable
        if (likeCount < 0) {
            throw new IllegalArgumentException("likeCount must be non-negative");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Comment aggregate + viewer-context (likedByMe) + UserSummary (authorDisplayName) 조립.
     *
     * @param authorDisplayName fallback 처리는 호출자가 (UserSummary NOT_FOUND 시 e.g. "(unknown)")
     */
    public static CommentView from(Comment comment, String authorDisplayName, boolean likedByMe) {
        return new CommentView(
            comment.id().value(),
            comment.diaryId().value(),
            comment.authorId(),
            authorDisplayName,
            comment.content().value(),
            comment.parentId().map(p -> p.value()).orElse(null),
            comment.likeCount(),
            likedByMe,
            comment.createdAt()
        );
    }
}
