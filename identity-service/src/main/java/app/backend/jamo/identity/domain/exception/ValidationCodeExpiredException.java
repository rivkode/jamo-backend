package app.backend.jamo.identity.domain.exception;

/**
 * 검증코드 TTL(5분)이 만료되었거나 저장된 코드가 없을 때.
 *
 * <p>SPA 는 본 예외에 매핑된 ErrorCode 를 받아 재발급 안내 (PRD user/validateEmail.md §9).
 */
public class ValidationCodeExpiredException extends EmailValidationException {

    public ValidationCodeExpiredException(String message) {
        super(message);
    }
}
