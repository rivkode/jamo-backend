package app.backend.jamo.identity.domain.exception;

/**
 * LOCAL login 실패 시도 한도 초과.
 *
 * <p>응답에는 남은 시간, email 존재 여부, attempts 수를 노출하지 않는다.
 */
public class LoginRateLimitedException extends RuntimeException {

    public LoginRateLimitedException(String message) {
        super(message);
    }
}
