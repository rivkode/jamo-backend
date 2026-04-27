package app.backend.jamo.identity.domain.exception;

/**
 * 이메일 검증 흐름(코드 발급/대조/만료/시도제한/rate limit) 도메인 예외 base.
 *
 * <p>Refresh 흐름의 {@link RefreshTokenException} 과 의미·스코프가 다르므로 별도 계층.
 * Presentation 의 ExceptionHandler 는 본 base 를 catch 한 뒤 sub 별로 ErrorCode 분기.
 */
public class EmailValidationException extends RuntimeException {

    public EmailValidationException(String message) {
        super(message);
    }

    public EmailValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
