package app.backend.jamo.diary.application.dto.comment;

import java.util.List;
import java.util.Objects;

/**
 * 댓글 목록 응답 view (박제 §6).
 *
 * <p>{@code nextCursor} 는 base64 opaque 문자열 — Application 의 {@code CommentCursorCodec} 이 인코딩.
 * {@code hasNext} 는 다음 페이지 존재 여부 (마지막 페이지면 false). flat list — 클라이언트가 parentId 로 트리 구성.
 */
public record CommentListView(List<CommentView> items, String nextCursor, boolean hasNext) {

    public CommentListView {
        Objects.requireNonNull(items, "items");
        items = List.copyOf(items);
    }
}
