package app.backend.jamo.diary.presentation.dto;

import app.backend.jamo.diary.application.dto.comment.CommentView;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 댓글 단건 응답 (POST /diaries/{id}/comments, list items).
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §7 + PRD 0526_flutter.md §3 Comment 스키마.
 *
 * <p>9 필드 (author 객체 내부 3 필드 포함): {@code commentId, diaryId, author{userId,username,avatarUrl},
 * text, createdAt, parentCommentId, likeCount, userLiked}.
 *
 * <p><b>필드 매핑</b> (Application {@link CommentView} → 본 record):
 * <ul>
 *   <li>{@code text} ← {@code content}</li>
 *   <li>{@code author.username} ← {@code authorDisplayName}</li>
 *   <li>{@code author.avatarUrl} ← null (avatar 도메인 미구현)</li>
 *   <li>{@code userLiked} ← {@code likedByMe}</li>
 *   <li>{@code parentCommentId} ← {@code parentId} (nullable)</li>
 * </ul>
 *
 * @param parentCommentId nullable (루트 댓글이면 null)
 */
public record CommentResponse(
    UUID commentId,
    UUID diaryId,
    CommentAuthor author,
    String text,
    Instant createdAt,
    UUID parentCommentId,
    int likeCount,
    boolean userLiked
) {

    public CommentResponse {
        Objects.requireNonNull(commentId, "commentId");
        Objects.requireNonNull(diaryId, "diaryId");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(createdAt, "createdAt");
        if (likeCount < 0) {
            throw new IllegalArgumentException("likeCount must be non-negative");
        }
    }

    public static CommentResponse from(CommentView view) {
        return new CommentResponse(
            view.commentId(),
            view.diaryId(),
            new CommentAuthor(view.authorId(), view.authorDisplayName(), null),
            view.content(),
            view.createdAt(),
            view.parentId(),
            view.likeCount(),
            view.likedByMe()
        );
    }
}
