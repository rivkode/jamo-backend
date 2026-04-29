package app.backend.jamo.diary.domain.exception;

/**
 * Diary Aggregate 부재 또는 비공개 + 비작성자 접근 시 발생 — 두 케이스 모두 동일 예외로 통일.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §2 (404 통일 IDOR 보호) + §9.
 *
 * <p>Presentation 매핑: HTTP 404 — 자원 존재 비노출.
 */
public class DiaryNotFoundException extends RuntimeException {
    public DiaryNotFoundException(String message) {
        super(message);
    }
}
