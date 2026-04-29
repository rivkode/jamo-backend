package app.backend.jamo.identity.presentation.exception;

import app.backend.jamo.identity.domain.exception.AuthenticatedUserNotFoundException;
import app.backend.jamo.identity.domain.exception.DisplayNameChangeTooFrequentException;
import app.backend.jamo.identity.domain.exception.UserNotFoundException;
import app.backend.jamo.identity.presentation.controller.ProfileController;
import app.backend.jamo.identity.presentation.dto.AuthErrorCode;
import app.backend.jamo.identity.presentation.dto.AuthErrorResponse;
import app.backend.jamo.identity.presentation.dto.ProfileErrorCode;
import app.backend.jamo.identity.presentation.dto.ProfileErrorResponse;
import app.backend.jamo.identity.presentation.web.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Profile presentation 계층의 ExceptionHandler.
 *
 * <p>{@code assignableTypes = ProfileController} 한정 — auth/user advice 와 매핑 충돌 회피
 * (User domain 의 {@link IllegalArgumentException} 핸들러가 profile 도메인 VO IAE 까지 잡지
 * 않도록 명시적 격리). 새 profile controller 추가 시 본 어노테이션의 {@code assignableTypes}
 * 에 명시 추가 필수.
 *
 * <p><b>UserNotFoundException 의미 통일</b> (PR6-c 결정 박제):
 * <ul>
 *   <li>{@link AuthenticatedUserNotFoundException} → 500 — 인증된 토큰의 userId 가 DB 부재 = 시스템 invariant 위반</li>
 *   <li>{@link UserNotFoundException} → 404 — 입력 검증 실패 (`/{userId}` 대상 부재)</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice(assignableTypes = {ProfileController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProfileExceptionHandler {

    @ExceptionHandler(DisplayNameChangeTooFrequentException.class)
    public ResponseEntity<ProfileErrorResponse> handleDisplayNameChangeTooFrequent(
            DisplayNameChangeTooFrequentException ex) {
        log.warn("display name change rate limit hit reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(new ProfileErrorResponse(
                        ProfileErrorCode.DISPLAY_NAME_CHANGE_TOO_FREQUENT,
                        "display name change is allowed once per 7 days"));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ProfileErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        log.warn("public profile target not found reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ProfileErrorResponse(
                        ProfileErrorCode.USER_NOT_FOUND,
                        "user not found"));
    }

    @ExceptionHandler(AuthenticatedUserNotFoundException.class)
    public ResponseEntity<ProfileErrorResponse> handleAuthenticatedUserNotFound(
            AuthenticatedUserNotFoundException ex) {
        // 시스템 invariant 위반 — error 레벨 + alarm trigger 가능
        log.error("authenticated user not found in DB — possible system invariant violation", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ProfileErrorResponse(
                        ProfileErrorCode.INTERNAL_ERROR,
                        "internal server error"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProfileErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("profile request validation failed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(new ProfileErrorResponse(
                        ProfileErrorCode.VALIDATION_FAILED,
                        "request body is invalid"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProfileErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("profile request body malformed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(new ProfileErrorResponse(
                        ProfileErrorCode.VALIDATION_FAILED,
                        "request body is malformed"));
    }

    /**
     * Domain VO ({@code Bio} / {@code AvatarUrl} / {@code Locale} / {@code DisplayName}) 가
     * 던지는 IAE 매핑. 또한 path userId UUID parsing 실패 IAE 도 본 핸들러로 매핑.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProfileErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("profile request rejected by domain validation reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(new ProfileErrorResponse(
                        ProfileErrorCode.VALIDATION_FAILED,
                        "request is invalid"));
    }

    /**
     * 인증 실패 — {@code @LoginUser} resolver 가 헤더 부재/만료/위조 시 던짐.
     *
     * <p>본 핸들러를 ProfileExceptionHandler 에 두는 이유: assignableTypes + HIGHEST_PRECEDENCE
     * 가 ProfileController 발생 예외를 우선 처리하므로 AuthExceptionHandler 까지 도달 안 함.
     * 인증 응답 일관성을 위해 {@code AuthErrorResponse} 로 직접 응답.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<AuthErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        log.warn("unauthorized profile request reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponse(
                        AuthErrorCode.UNAUTHORIZED,
                        "authentication required"));
    }

    /**
     * Profile controller 한정 fallback — assignableTypes + HIGHEST_PRECEDENCE 덕에 본 핸들러는
     * ProfileController 발생 예외만 잡는다. Auth advice 의 generic 으로 흘러가서 AuthErrorCode 가
     * profile endpoint 응답에 노출되는 도메인 누수 차단.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProfileErrorResponse> handleGeneric(Exception ex) {
        log.error("unhandled exception in profile presentation", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ProfileErrorResponse(
                        ProfileErrorCode.INTERNAL_ERROR,
                        "internal server error"));
    }
}
