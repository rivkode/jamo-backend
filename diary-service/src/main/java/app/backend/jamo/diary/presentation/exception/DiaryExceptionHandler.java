package app.backend.jamo.diary.presentation.exception;

import app.backend.jamo.diary.application.cursor.InvalidDiaryFeedCursorException;
import app.backend.jamo.diary.domain.exception.DiaryAccessDeniedException;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.exception.InvalidDiaryContentException;
import app.backend.jamo.diary.domain.exception.InvalidImageUrlException;
import app.backend.jamo.diary.domain.exception.InvalidTagException;
import app.backend.jamo.diary.presentation.controller.DiaryController;
import app.backend.jamo.diary.presentation.controller.DiaryLikeController;
import app.backend.jamo.diary.presentation.dto.DiaryErrorCode;
import app.backend.jamo.diary.presentation.dto.DiaryErrorResponse;
import app.backend.jamo.diary.presentation.web.UnauthorizedException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * diary core presentation 계층의 ExceptionHandler.
 *
 * <p>{@code assignableTypes = {DiaryController, DiaryLikeController}} 한정 + HIGHEST_PRECEDENCE — 같은
 * diary-service 내의 다른 sub-domain ({@code DiarySentenceFeedbackController}) ExceptionHandler 와 매핑
 * 충돌 회피. sentence-feedback {@code SentenceFeedbackExceptionHandler} 패턴 정합.
 *
 * <p><b>매핑</b> (박제: decisions/diary/diary-domain-policy.md):
 * <ul>
 *   <li>{@link DiaryNotFoundException}, {@link DiaryAccessDeniedException} → 404 DIARY_NOT_FOUND
 *       (§2 / §9 IDOR 통일)</li>
 *   <li>{@link InvalidDiaryContentException}, {@link InvalidTagException},
 *       {@link InvalidImageUrlException} → 400 DIARY_VALIDATION_FAILED (§3 도메인 invariant)</li>
 *   <li>{@link InvalidDiaryFeedCursorException} → 400 DIARY_VALIDATION_FAILED (§7)</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 (Bean Validation, body)</li>
 *   <li>{@link ConstraintViolationException} → 400 (query / path Bean Validation)</li>
 *   <li>{@link HttpMessageNotReadableException} → 400 (malformed body)</li>
 *   <li>{@link MethodArgumentTypeMismatchException} → 400 (path/query type 불일치)</li>
 *   <li>{@link IllegalArgumentException} → 400 (UUID parse 실패 / size out-of-range / sort 값 / 기타)</li>
 *   <li>{@link UnauthorizedException} → 401 UNAUTHORIZED (LoginUserArgumentResolver)</li>
 *   <li>{@link Exception} fallback → 500 INTERNAL_ERROR (sanitization)</li>
 * </ul>
 *
 * <p><b>매핑 누락 알림</b> (code-reviewer L2): 본 PR 시점 모든 query 가 optional 또는 default 값 보유라
 * {@code MissingServletRequestParameterException} 매핑이 없다. 향후 필수 query 추가 시 본 핸들러에 명시 매핑
 * 필요 — 누락 시 fallback {@code Exception} → 500 으로 잘못 매핑됨.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = {DiaryController.class, DiaryLikeController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DiaryExceptionHandler {

    @ExceptionHandler({DiaryNotFoundException.class, DiaryAccessDeniedException.class})
    public ResponseEntity<DiaryErrorResponse> handleNotFound(RuntimeException ex) {
        log.warn("diary not found / access denied reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new DiaryErrorResponse(DiaryErrorCode.DIARY_NOT_FOUND, "diary not found"));
    }

    @ExceptionHandler({
        InvalidDiaryContentException.class,
        InvalidTagException.class,
        InvalidImageUrlException.class,
        InvalidDiaryFeedCursorException.class
    })
    public ResponseEntity<DiaryErrorResponse> handleDomainValidation(RuntimeException ex) {
        log.warn("diary domain validation failed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new DiaryErrorResponse(DiaryErrorCode.DIARY_VALIDATION_FAILED, "request is invalid"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<DiaryErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        log.warn("diary request body validation failed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new DiaryErrorResponse(
                DiaryErrorCode.DIARY_VALIDATION_FAILED, "request body is invalid"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<DiaryErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("diary query/path validation failed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new DiaryErrorResponse(
                DiaryErrorCode.DIARY_VALIDATION_FAILED, "request parameter is invalid"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<DiaryErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("diary request body malformed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new DiaryErrorResponse(
                DiaryErrorCode.DIARY_VALIDATION_FAILED, "request body is malformed"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<DiaryErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("diary path/query type mismatch reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new DiaryErrorResponse(
                DiaryErrorCode.DIARY_VALIDATION_FAILED, "request parameter is invalid"));
    }

    /**
     * UUID parse 실패 / size out-of-range / 잘못된 sort 값 / Tag VO invariant 위반 등 IAE 통합 매핑.
     * Controller 가 raw String 을 도메인/Application 형식으로 변환할 때 던짐.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<DiaryErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("diary request rejected reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new DiaryErrorResponse(DiaryErrorCode.DIARY_VALIDATION_FAILED, "request is invalid"));
    }

    /**
     * 인증 실패 — assignableTypes + HIGHEST_PRECEDENCE 덕에 본 핸들러가 우선 매핑.
     * sentence-feedback 패턴 정합 (도메인 누수 차단).
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<DiaryErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        log.warn("unauthorized diary request reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new DiaryErrorResponse(DiaryErrorCode.UNAUTHORIZED, "authentication required"));
    }

    /**
     * Controller 한정 fallback — diary core 외 다른 generic advice 로 누수 차단.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<DiaryErrorResponse> handleGeneric(Exception ex) {
        log.error("unhandled exception in diary core presentation", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new DiaryErrorResponse(DiaryErrorCode.INTERNAL_ERROR, "internal server error"));
    }
}
