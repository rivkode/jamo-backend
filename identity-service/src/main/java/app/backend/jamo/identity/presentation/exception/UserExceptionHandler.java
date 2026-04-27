package app.backend.jamo.identity.presentation.exception;

import app.backend.jamo.identity.domain.exception.EmailAlreadyRegisteredException;
import app.backend.jamo.identity.domain.exception.EmailNotValidatedException;
import app.backend.jamo.identity.domain.exception.ValidationCodeExpiredException;
import app.backend.jamo.identity.domain.exception.ValidationCodeLockedException;
import app.backend.jamo.identity.domain.exception.ValidationCodeMismatchException;
import app.backend.jamo.identity.domain.exception.ValidationRateLimitedException;
import app.backend.jamo.identity.presentation.controller.UserRegistrationController;
import app.backend.jamo.identity.presentation.controller.UserValidationController;
import app.backend.jamo.identity.presentation.dto.UserErrorCode;
import app.backend.jamo.identity.presentation.dto.UserErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * User presentation 계층의 ExceptionHandler — 이메일 검증 / 회원가입 도메인 예외 + Bean Validation 매핑.
 *
 * <p>{@code assignableTypes = {UserValidationController, UserRegistrationController}} 로
 * user 도메인 controller 한정 적용 — auth advice 와 매핑 충돌 회피
 * (e.g. {@link MethodArgumentNotValidException}). 새 user controller 추가 시 본 어노테이션의
 * {@code assignableTypes} 에 명시 추가 필수.
 *
 * <p><b>핵심 원칙</b> (PR5-b security review H2 / PR6-c):
 * 메시지 / attempts / 잔여 횟수 등 진행 정보를 응답에 포함하지 않는다 — ErrorCode 만 노출.
 * 서버 로그 (warn) 에는 진단용 정보 남김 (PII 마스킹).
 */
@RestControllerAdvice(assignableTypes = {UserValidationController.class, UserRegistrationController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UserExceptionHandler.class);

    @ExceptionHandler(ValidationCodeMismatchException.class)
    public ResponseEntity<UserErrorResponse> handleMismatch(ValidationCodeMismatchException ex) {
        log.warn("validation code mismatch reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(new UserErrorResponse(
                        UserErrorCode.VALIDATION_CODE_MISMATCH,
                        "validation code is invalid"));
    }

    @ExceptionHandler(ValidationCodeExpiredException.class)
    public ResponseEntity<UserErrorResponse> handleExpired(ValidationCodeExpiredException ex) {
        log.warn("validation code expired reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(new UserErrorResponse(
                        UserErrorCode.VALIDATION_CODE_EXPIRED,
                        "validation code is expired or not issued"));
    }

    @ExceptionHandler(ValidationCodeLockedException.class)
    public ResponseEntity<UserErrorResponse> handleLocked(ValidationCodeLockedException ex) {
        log.warn("validation code locked reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(new UserErrorResponse(
                        UserErrorCode.VALIDATION_CODE_LOCKED,
                        "validation code is locked, please request a new one"));
    }

    @ExceptionHandler(ValidationRateLimitedException.class)
    public ResponseEntity<UserErrorResponse> handleRateLimited(ValidationRateLimitedException ex) {
        log.warn("validation send rate limited reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new UserErrorResponse(
                        UserErrorCode.VALIDATION_RATE_LIMITED,
                        "too many requests, please try again later"));
    }

    @ExceptionHandler(EmailNotValidatedException.class)
    public ResponseEntity<UserErrorResponse> handleEmailNotValidated(EmailNotValidatedException ex) {
        // PRD §9 + decisions/identity/local-credential-deployment-checklist.md 결정 3:
        // 미발급/만료 사유는 enumeration 회피를 위해 응답에 분리하지 않음.
        log.warn("create user rejected: email not validated reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(new UserErrorResponse(
                        UserErrorCode.EMAIL_NOT_VALIDATED,
                        "email validation required before sign-up"));
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<UserErrorResponse> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        // accepted risk: LOCAL 가입자 enumeration window 존재
        // (decisions/identity/local-credential-deployment-checklist.md 결정 3).
        log.warn("create user rejected: email already registered reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new UserErrorResponse(
                        UserErrorCode.EMAIL_ALREADY_REGISTERED,
                        "email is already registered"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<UserErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // BindingResult 의 user input 일부가 ex.toString() 에 포함될 수 있어 클래스명만 로깅 (CWE-532).
        log.warn("user request validation failed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(new UserErrorResponse(
                        UserErrorCode.VALIDATION_FAILED,
                        "request body is invalid"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<UserErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        // Jackson 의 message 가 raw input 일부를 포함할 수 있어 ex 객체 자체 로깅 회피 (CWE-532).
        log.warn("user request body malformed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(new UserErrorResponse(
                        UserErrorCode.VALIDATION_FAILED,
                        "request body is malformed"));
    }

    /**
     * Domain VO ({@code Email} / {@code ValidationCode}) 가 던지는 IAE 매핑.
     *
     * <p>{@code @Email} (Hibernate Validator default, lax) 는 {@code foo@bar} 같이 dot 없는
     * 도메인을 통과시키지만 Domain {@code Email} VO 정규식은 거부 → IAE 발생. 본 핸들러 부재 시
     * Auth advice 의 generic Exception fallback 이 500 + AuthErrorCode.INTERNAL_ERROR 로
     * 응답해 도메인 누수 발생 (PR5-c security review H1).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<UserErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("user request rejected by domain validation reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(new UserErrorResponse(
                        UserErrorCode.VALIDATION_FAILED,
                        "request body is invalid"));
    }

    /**
     * User controller 한정 fallback — assignableTypes + HIGHEST_PRECEDENCE 덕에 본 핸들러는
     * 명시된 user controller 발생 예외만 잡는다. Auth advice 의 generic 으로 흘러가서
     * AuthErrorCode 가 user endpoint 응답에 노출되는 도메인 누수 차단 (PR5-c security review M2).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<UserErrorResponse> handleGeneric(Exception ex) {
        log.error("unhandled exception in user presentation", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new UserErrorResponse(
                        UserErrorCode.INTERNAL_ERROR,
                        "internal server error"));
    }
}
