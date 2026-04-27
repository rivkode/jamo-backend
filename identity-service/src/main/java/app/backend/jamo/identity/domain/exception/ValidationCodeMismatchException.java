package app.backend.jamo.identity.domain.exception;

/**
 * 입력된 검증코드가 저장된 코드와 불일치할 때.
 *
 * <p>시도 횟수는 호출자(application service)가 카운터를 증가시킨다. 시도가 한도를 초과하면
 * {@link ValidationCodeLockedException} 으로 격상 (PRD user/validateEmail.md §9).
 */
public class ValidationCodeMismatchException extends EmailValidationException {

    public ValidationCodeMismatchException(String message) {
        super(message);
    }
}
