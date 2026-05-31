package app.backend.jamo.diary.domain.exception;

/**
 * DiaryLines VO invariant 위반 — 줄 개수가 정확히 3 이 아님.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §3 (lines 정확히 3줄) + PRD 0526_flutter.md §2.3.
 *
 * <p>Presentation 매핑: HTTP <b>422</b> (INVALID_LINE_COUNT) — 구조적으로 처리 불가한 요청.
 */
public class InvalidLineCountException extends RuntimeException {
    public InvalidLineCountException(String message) {
        super(message);
    }
}
