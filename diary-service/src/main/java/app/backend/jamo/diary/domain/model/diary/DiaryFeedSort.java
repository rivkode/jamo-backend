package app.backend.jamo.diary.domain.model.diary;

/**
 * 공개 피드 정렬 기준.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §7.
 *
 * <ul>
 *   <li>{@link #RECENT} — {@code created_at desc} (default). cursor: {@code (created_at, diary_id)}</li>
 *   <li>{@link #POPULAR} — {@code like_count desc, created_at desc} tiebreak. cursor: {@code (like_count, created_at, diary_id)}</li>
 * </ul>
 *
 * <p>본인 피드 ({@code listMyFeed}) 는 {@link #RECENT} 단일 — sort 옵션 미노출.
 *
 * <p>popular 의 시간 가중치 (week / month) 는 운영 모니터링 후 후속 결정 (코드 슬라이스 시점 보류).
 */
public enum DiaryFeedSort {
    RECENT,
    POPULAR;

    /** 박제 §7 — sort 미지정 시 default. Application/Presentation 단의 null fallback 코드 중복 회피. */
    public static DiaryFeedSort defaultSort() {
        return RECENT;
    }
}
