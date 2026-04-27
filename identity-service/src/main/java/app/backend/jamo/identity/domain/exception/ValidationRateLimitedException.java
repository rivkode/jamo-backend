package app.backend.jamo.identity.domain.exception;

/**
 * 코드 발송 rate limit(per-email 30초당 1회 + 1일 10회) 초과.
 *
 * <p>HTTP 429 매핑. PRD user/sendValidationNumber.md §9 의 spam 방지 정책.
 */
public class ValidationRateLimitedException extends EmailValidationException {

    public ValidationRateLimitedException(String message) {
        super(message);
    }
}
