package app.backend.jamo.diary.presentation.dto;

import app.backend.jamo.diary.application.dto.comment.CommentListView;

import java.util.List;
import java.util.Objects;

/**
 * 댓글 목록 응답 (GET /diaries/{id}/comments).
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §6 (flat list + cursor opaque).
 *
 * <p>{@link FeedResponse} 의 평탄 구조 정합 — PRD 0526_flutter.md §3.1 의 {@code paging} nested 표기는
 * Slice 2 alias PR 에서 getter 형태로 동시 노출 예정 (FeedResponse 와 일관 정책).
 */
public record CommentListResponse(List<CommentResponse> items, String nextCursor, boolean hasNext) {

    public CommentListResponse {
        Objects.requireNonNull(items, "items");
        items = List.copyOf(items);
    }

    public static CommentListResponse from(CommentListView view) {
        List<CommentResponse> items = view.items().stream()
            .map(CommentResponse::from)
            .toList();
        return new CommentListResponse(items, view.nextCursor(), view.hasNext());
    }
}
