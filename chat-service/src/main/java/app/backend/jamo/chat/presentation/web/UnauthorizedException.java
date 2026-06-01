package app.backend.jamo.chat.presentation.web;

/**
 * Authorization 헤더 부재/만료/위조/blacklist sid 등 인증 실패 통일 표현. ChatExceptionHandler 가 401 매핑.
 * 구체 사유는 message 로 server-side log 만, 응답 비노출.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
