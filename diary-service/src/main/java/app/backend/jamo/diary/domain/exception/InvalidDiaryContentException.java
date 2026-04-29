package app.backend.jamo.diary.domain.exception;

/**
 * DiaryContent VO invariant 위반 — 길이 (1..2000 code points) 또는 blank.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §3.
 *
 * <p>Presentation 매핑: HTTP 400.
 */
public class InvalidDiaryContentException extends RuntimeException {
    public InvalidDiaryContentException(String message) {
        super(message);
    }
}
