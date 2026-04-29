package app.backend.jamo.diary.domain.model.diary;

/**
 * 일기 공개 범위.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §2.
 *
 * <p><b>가드 동작</b>:
 * <ul>
 *   <li>{@link #PUBLIC} — 누구나 조회 (피드 노출 + 단건 조회 + 좋아요)</li>
 *   <li>{@link #PRIVATE} — 작성자만 조회 가능. 비작성자 접근은 404 통일 (IDOR 보호) — 자원 존재 비노출</li>
 * </ul>
 *
 * <p>{@code FOLLOWERS_ONLY} 는 follow 도메인 미존재 → 후속 (Non-Goals).
 *
 * <p>피드 쿼리는 {@code WHERE visibility = PUBLIC} 으로 비공개 자동 제외 — 본인 listMyFeed 는 visibility 무관.
 */
public enum Visibility {
    PUBLIC,
    PRIVATE;
}
