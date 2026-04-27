package app.backend.jamo.identity.domain.exception;

/**
 * 검증 시도가 한도(5회) 를 초과해 코드가 잠긴 상태.
 *
 * <p>호출자는 본 예외 발생 시 코드를 무효화(invalidate)하고 사용자에게 재발급 안내 — PRD user/validateEmail.md §9
 * 의 brute-force 방지 정책.
 */
public class ValidationCodeLockedException extends EmailValidationException {

    public ValidationCodeLockedException(String message) {
        super(message);
    }
}
