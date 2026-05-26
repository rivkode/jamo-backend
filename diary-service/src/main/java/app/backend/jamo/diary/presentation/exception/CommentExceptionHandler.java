package app.backend.jamo.diary.presentation.exception;

import app.backend.jamo.diary.application.cursor.InvalidCommentCursorException;
import app.backend.jamo.diary.domain.exception.CommentAccessDeniedException;
import app.backend.jamo.diary.domain.exception.CommentNotFoundException;
import app.backend.jamo.diary.domain.exception.DiaryAccessDeniedException;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.exception.InvalidCommentContentException;
import app.backend.jamo.diary.domain.exception.InvalidCommentParentException;
import app.backend.jamo.diary.presentation.controller.CommentController;
import app.backend.jamo.diary.presentation.controller.CommentLikeController;
import app.backend.jamo.diary.presentation.dto.CommentErrorCode;
import app.backend.jamo.diary.presentation.dto.CommentErrorResponse;
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
 * comment presentation 계층의 ExceptionHandler.
 *
 * <p>{@code assignableTypes = {CommentController, CommentLikeController}} 한정 + HIGHEST_PRECEDENCE — 같은
 * diary-service 내의 {@code DiaryExceptionHandler}, {@code SentenceFeedbackExceptionHandler} 와 매핑 충돌 회피.
 *
 * <p><b>매핑</b> (박제: decisions/diary/comment-domain-policy.md):
 * <ul>
 *   <li>{@link CommentNotFoundException}, {@link CommentAccessDeniedException},
 *       {@link DiaryNotFoundException}, {@link DiaryAccessDeniedException} → 404 COMMENT_NOT_FOUND
 *       (§4 IDOR 통일 — Comment 진입점이므로 Diary 가드 실패도 본 코드로 응답)</li>
 *   <li>{@link InvalidCommentContentException}, {@link InvalidCommentParentException},
 *       {@link InvalidCommentCursorException} → 400 COMMENT_VALIDATION_FAILED</li>
 *   <li>Bean Validation / IAE / malformed body / type mismatch → 400 COMMENT_VALIDATION_FAILED</li>
 *   <li>{@link UnauthorizedException} → 401 UNAUTHORIZED</li>
 *   <li>{@link Exception} fallback → 500 INTERNAL_ERROR</li>
 * </ul>
 *
 * <p><b>매핑 누락 알림</b> (code-reviewer L1 / DiaryExceptionHandler 정합): 본 PR 시점 모든 query 가 optional
 * 또는 default 값 보유라 {@code MissingServletRequestParameterException} 매핑이 없다. 향후 필수 query 추가 시
 * 본 핸들러에 명시 매핑 필요 — 누락 시 fallback {@code Exception} → 500 으로 잘못 매핑됨.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = {CommentController.class, CommentLikeController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CommentExceptionHandler {

    @ExceptionHandler({
        CommentNotFoundException.class,
        CommentAccessDeniedException.class,
        DiaryNotFoundException.class,
        DiaryAccessDeniedException.class
    })
    public ResponseEntity<CommentErrorResponse> handleNotFound(RuntimeException ex) {
        log.warn("comment not found / access denied reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new CommentErrorResponse(CommentErrorCode.COMMENT_NOT_FOUND, "comment not found"));
    }

    @ExceptionHandler({
        InvalidCommentContentException.class,
        InvalidCommentParentException.class,
        InvalidCommentCursorException.class
    })
    public ResponseEntity<CommentErrorResponse> handleDomainValidation(RuntimeException ex) {
        log.warn("comment domain validation failed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new CommentErrorResponse(
                CommentErrorCode.COMMENT_VALIDATION_FAILED, "request is invalid"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommentErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        log.warn("comment request body validation failed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new CommentErrorResponse(
                CommentErrorCode.COMMENT_VALIDATION_FAILED, "request body is invalid"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CommentErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("comment query/path validation failed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new CommentErrorResponse(
                CommentErrorCode.COMMENT_VALIDATION_FAILED, "request parameter is invalid"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommentErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("comment request body malformed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new CommentErrorResponse(
                CommentErrorCode.COMMENT_VALIDATION_FAILED, "request body is malformed"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CommentErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("comment path/query type mismatch reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new CommentErrorResponse(
                CommentErrorCode.COMMENT_VALIDATION_FAILED, "request parameter is invalid"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommentErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("comment request rejected reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new CommentErrorResponse(
                CommentErrorCode.COMMENT_VALIDATION_FAILED, "request is invalid"));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<CommentErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        log.warn("unauthorized comment request reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new CommentErrorResponse(CommentErrorCode.UNAUTHORIZED, "authentication required"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommentErrorResponse> handleGeneric(Exception ex) {
        log.error("unhandled exception in comment presentation", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new CommentErrorResponse(CommentErrorCode.INTERNAL_ERROR, "internal server error"));
    }
}
