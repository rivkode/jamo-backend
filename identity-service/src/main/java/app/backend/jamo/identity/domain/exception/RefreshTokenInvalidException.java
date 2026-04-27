package app.backend.jamo.identity.domain.exception;

/**
 * Refresh JWT 가 위조되었거나(서명/형식 불일치), 저장된 hash 와 다르거나,
 * 이미 폐기된 sessionId 인 경우 발생.
 *
 * <p>재사용(reuse) 으로 의심되는 케이스는 {@link RefreshTokenReuseDetectedException}
 * 으로 분리해 보안 모니터링과 보상 트랜잭션을 구분한다.
 */
public class RefreshTokenInvalidException extends RefreshTokenException {

    public RefreshTokenInvalidException(String message) {
        super(message);
    }

    public RefreshTokenInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
