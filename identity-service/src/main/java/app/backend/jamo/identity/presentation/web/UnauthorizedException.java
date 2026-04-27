package app.backend.jamo.identity.presentation.web;

/**
 * Authorization 헤더 부재/만료/위조/blacklist sid 등 인증 실패를 통일적으로 표현.
 * AuthExceptionHandler 가 401 + {@code UNAUTHORIZED} ErrorCode 로 매핑.
 *
 * <p>구체 사유는 {@code message} 에 두어 server-side log 로만 흐르고 응답에는 노출하지 않는다.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
