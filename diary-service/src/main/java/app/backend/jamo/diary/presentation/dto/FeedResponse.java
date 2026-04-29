package app.backend.jamo.diary.presentation.dto;

import app.backend.jamo.diary.application.dto.diary.FeedView;

import java.util.List;
import java.util.Objects;

/**
 * 피드 응답 (GET /diaries/feed, GET /diaries/me).
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §7.
 *
 * <p>{@code items} 는 풀 content 반환 (박제 §G — preview truncate 미적용, 클라가 자체 truncate).
 * {@code nextCursor} 는 base64 opaque (Application {@code DiaryFeedCursorCodec} 인코딩 결과). {@code hasNext}
 * 가 false 면 마지막 페이지.
 */
public record FeedResponse(List<DiaryResponse> items, String nextCursor, boolean hasNext) {

    public FeedResponse {
        Objects.requireNonNull(items, "items");
        items = List.copyOf(items);
    }

    public static FeedResponse from(FeedView view) {
        List<DiaryResponse> items = view.items().stream()
            .map(DiaryResponse::from)
            .toList();
        return new FeedResponse(items, view.nextCursor(), view.hasNext());
    }
}
