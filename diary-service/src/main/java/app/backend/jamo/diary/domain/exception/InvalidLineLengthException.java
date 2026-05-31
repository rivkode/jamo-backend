package app.backend.jamo.diary.domain.exception;

/**
 * DiaryLines VO invariant 위반 — 한 줄의 길이가 1..200 code points 범위 밖이거나 blank.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §3 (각 줄 1..200cp) + PRD 0526_flutter.md §2.3.
 *
 * <p>Presentation 매핑: HTTP <b>400</b> (INVALID_LINE_LENGTH).
 */
public class InvalidLineLengthException extends RuntimeException {
    public InvalidLineLengthException(String message) {
        super(message);
    }
}
