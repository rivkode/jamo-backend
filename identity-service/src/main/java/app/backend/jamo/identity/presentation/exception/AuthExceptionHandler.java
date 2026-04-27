package app.backend.jamo.identity.presentation.exception;

import app.backend.jamo.identity.domain.exception.AuthCodeExpiredException;
import app.backend.jamo.identity.domain.exception.AuthCodeNotFoundException;
import app.backend.jamo.identity.domain.exception.OAuthAuthenticationException;
import app.backend.jamo.identity.presentation.dto.AuthErrorCode;
import app.backend.jamo.identity.presentation.dto.AuthErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Auth presentation 계층의 ExceptionHandler.
 *
 * <p>Exchange JSON 응답만 매핑. Callback 의 OAuth 예외는 {@code OAuthBrowserController}
 * 가 try-catch 후 frontend redirect URL 로 직접 처리하므로 본 handler 에 도달하지 않는 것이 정상.
 * 본 handler 의 {@link #handleOAuthAuthentication} 는 안전망 (예외가 흘러나와도 raw message 누출 차단).
 *
 * <p>핵심 원칙 (PR3-a security H1 의 호출자 의무):
 * <b>도메인 예외의 raw message / cause stack 을 응답에 노출하지 않는다.</b>
 * 모든 응답은 고정 ErrorCode 와 generic message 만.
 */
@RestControllerAdvice(basePackages = "app.backend.jamo.identity.presentation")
public class AuthExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);

    @ExceptionHandler({AuthCodeNotFoundException.class, AuthCodeExpiredException.class})
    public ResponseEntity<AuthErrorResponse> handleAuthCodeInvalid(Exception ex) {
        log.warn("auth exchange rejected reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponse(
                        AuthErrorCode.AUTH_CODE_INVALID,
                        "authorization code is invalid"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AuthErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
                .body(new AuthErrorResponse(
                        AuthErrorCode.VALIDATION_FAILED,
                        "request body is invalid"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AuthErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new AuthErrorResponse(
                        AuthErrorCode.VALIDATION_FAILED,
                        "request body is malformed"));
    }

    /**
     * 안전망 — start/callback 흐름은 이미 controller 가 redirect 처리. 만약 그 흐름이
     * 우회되어 본 handler 에 도달하면 generic 401 응답.
     */
    @ExceptionHandler(OAuthAuthenticationException.class)
    public ResponseEntity<AuthErrorResponse> handleOAuthAuthentication(OAuthAuthenticationException ex) {
        log.warn("oauth authentication exception leaked to JSON handler reason={}",
                ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponse(
                        AuthErrorCode.OAUTH_AUTHORIZATION_FAILED,
                        "oauth authentication failed"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AuthErrorResponse> handleGeneric(Exception ex) {
        log.error("unhandled exception in auth presentation", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AuthErrorResponse(
                        AuthErrorCode.INTERNAL_ERROR,
                        "internal server error"));
    }
}
