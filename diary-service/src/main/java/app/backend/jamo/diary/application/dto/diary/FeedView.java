package app.backend.jamo.diary.application.dto.diary;

import java.util.List;
import java.util.Objects;

/**
 * 피드 응답 view (listFeed / listMyFeed 공통).
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §7.
 *
 * <p>{@code nextCursor} 는 base64 opaque 문자열 — Presentation 의 cursor codec 이 인코딩. {@code hasNext}
 * 는 다음 페이지 존재 여부 (마지막 페이지면 false).
 *
 * <p>{@code DiaryItem = DiaryView} (풀 content 반환, 박제 §G — preview truncate 미적용).
 */
public record FeedView(List<DiaryView> items, String nextCursor, boolean hasNext) {

    public FeedView {
        Objects.requireNonNull(items, "items");
        items = List.copyOf(items);
    }
}
