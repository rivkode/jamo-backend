package app.backend.jamo.identity.presentation.exception;

import app.backend.jamo.identity.domain.exception.AuthCodeExpiredException;
import app.backend.jamo.identity.domain.exception.AuthCodeNotFoundException;
import app.backend.jamo.identity.domain.exception.LoginInvalidException;
import app.backend.jamo.identity.domain.exception.LoginRateLimitedException;
import app.backend.jamo.identity.domain.exception.OAuthAuthenticationException;
import app.backend.jamo.identity.domain.exception.RefreshTokenExpiredException;
import app.backend.jamo.identity.domain.exception.RefreshTokenInvalidException;
import app.backend.jamo.identity.domain.exception.RefreshTokenReuseDetectedException;
import app.backend.jamo.identity.presentation.dto.AuthErrorCode;
import app.backend.jamo.identity.presentation.dto.AuthErrorResponse;
import app.backend.jamo.identity.presentation.web.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RestControllerAdvice(basePackages = "app.backend.jamo.identity.presentation")
public class AuthExceptionHandler {

    @ExceptionHandler({AuthCodeNotFoundException.class, AuthCodeExpiredException.class})
    public ResponseEntity<AuthErrorResponse> handleAuthCodeInvalid(Exception ex) {
        log.warn("auth exchange rejected reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponse(
                        AuthErrorCode.AUTH_CODE_INVALID,
                        "authorization code is invalid"));
    }

    @ExceptionHandler(LoginInvalidException.class)
    public ResponseEntity<AuthErrorResponse> handleLoginInvalid(LoginInvalidException ex) {
        log.warn("local login rejected reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponse(
                        AuthErrorCode.LOGIN_INVALID,
                        "login credentials are invalid"));
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<AuthErrorResponse> handleLoginRateLimited(LoginRateLimitedException ex) {
        log.warn("local login rate limited reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new AuthErrorResponse(
                        AuthErrorCode.LOGIN_RATE_LIMITED,
                        "too many requests, please try again later"));
    }

    @ExceptionHandler(RefreshTokenExpiredException.class)
    public ResponseEntity<AuthErrorResponse> handleRefreshExpired(RefreshTokenExpiredException ex) {
        log.warn("refresh rejected reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponse(
                        AuthErrorCode.REFRESH_EXPIRED,
                        "refresh token expired"));
    }

    /**
     * Refresh JWT 위조/위반 + reuse detection 통합 401 응답.
     * reuse 감지 신호를 클라이언트에 노출하지 않는 보안 표준 (decisions Q2 — OWASP 권고).
     * 보상 트랜잭션 트리거는 이미 application service 가 server-side 로 수행 + 별도 log/메트릭.
     */
    @ExceptionHandler({RefreshTokenInvalidException.class, RefreshTokenReuseDetectedException.class})
    public ResponseEntity<AuthErrorResponse> handleRefreshInvalid(Exception ex) {
        log.warn("refresh rejected reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponse(
                        AuthErrorCode.REFRESH_INVALID,
                        "refresh token is invalid"));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<AuthErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        log.warn("authorization rejected reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponse(
                        AuthErrorCode.UNAUTHORIZED,
                        "authentication required"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AuthErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // BindingResult 의 user input 일부가 ex.toString() 에 포함될 수 있어 클래스명만 로깅 (CWE-532).
        log.warn("validation failed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(new AuthErrorResponse(
                        AuthErrorCode.VALIDATION_FAILED,
                        "request body is invalid"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AuthErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        // Jackson 의 message 가 raw refresh token 일부를 포함할 수 있어 ex 객체 자체 로깅 회피 (security M4, CWE-532).
        log.warn("request body malformed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(new AuthErrorResponse(
                        AuthErrorCode.VALIDATION_FAILED,
                        "request body is malformed"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AuthErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("auth request rejected by domain validation reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(new AuthErrorResponse(
                        AuthErrorCode.VALIDATION_FAILED,
                        "request body is invalid"));
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
