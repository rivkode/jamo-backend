package app.backend.jamo.diary.presentation.web;

/**
 * Authorization 헤더 부재/만료/위조/blacklist sid 등 인증 실패 통일 표현.
 *
 * <p>{@code SentenceFeedbackExceptionHandler} 가 401 매핑. 구체 사유는 message 에 두어 server-side
 * log 로만 흐르고 응답에는 노출하지 않는다 (보안 표준).
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
