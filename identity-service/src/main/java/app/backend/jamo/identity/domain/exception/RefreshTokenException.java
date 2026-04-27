package app.backend.jamo.identity.domain.exception;

/**
 * Refresh JWT 라이프사이클(검증/회전/재사용 감지) 중 발생하는 도메인 예외 base.
 *
 * <p>OAuth 흐름의 {@link OAuthAuthenticationException} 과 의미·스코프가 다르므로 별도 계층.
 * Presentation 의 ExceptionHandler 는 본 base 를 catch 한 뒤 sub 별로 ErrorCode 를 분기한다.
 */
public class RefreshTokenException extends RuntimeException {

    public RefreshTokenException(String message) {
        super(message);
    }

    public RefreshTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
