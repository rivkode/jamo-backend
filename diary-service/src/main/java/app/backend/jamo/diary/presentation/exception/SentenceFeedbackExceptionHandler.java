package app.backend.jamo.diary.presentation.exception;

import app.backend.jamo.diary.domain.exception.InvalidSentenceTextException;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackInvalidTransitionException;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackNotFoundException;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackRateLimitedException;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackUnknownSuggestionException;
import app.backend.jamo.diary.presentation.controller.DiarySentenceFeedbackController;
import app.backend.jamo.diary.presentation.dto.SentenceFeedbackErrorCode;
import app.backend.jamo.diary.presentation.dto.SentenceFeedbackErrorResponse;
import app.backend.jamo.diary.presentation.web.UnauthorizedException;
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
 * sentence-feedback presentation 계층의 ExceptionHandler.
 *
 * <p>{@code assignableTypes = DiarySentenceFeedbackController} 한정 + HIGHEST_PRECEDENCE — 다른 sub-domain
 * controller 가 후속 PR 에 추가될 때 매핑 충돌 회피. identity 의 ProfileExceptionHandler 패턴 정합.
 *
 * <p><b>매핑</b>:
 * <ul>
 *   <li>{@link SentenceFeedbackNotFoundException} → 404 SENTENCE_FEEDBACK_NOT_FOUND (IDOR 통일)</li>
 *   <li>{@link SentenceFeedbackInvalidTransitionException} → 409 SENTENCE_FEEDBACK_INVALID_TRANSITION</li>
 *   <li>{@link SentenceFeedbackUnknownSuggestionException} → 400 SENTENCE_FEEDBACK_UNKNOWN_SUGGESTION</li>
 *   <li>{@link SentenceFeedbackRateLimitedException} → 429 SENTENCE_FEEDBACK_RATE_LIMITED</li>
 *   <li>{@link InvalidSentenceTextException} → 400 SENTENCE_FEEDBACK_VALIDATION_FAILED (도메인 VO 위반)</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 (Bean Validation)</li>
 *   <li>{@link HttpMessageNotReadableException} → 400 (malformed body)</li>
 *   <li>{@link IllegalArgumentException} → 400 (UUID parse 실패 등 — Controller 에서 던져짐)</li>
 *   <li>{@link UnauthorizedException} → 401 (LoginUserArgumentResolver)</li>
 *   <li>{@link Exception} fallback → 500 INTERNAL_ERROR</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice(assignableTypes = {DiarySentenceFeedbackController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SentenceFeedbackExceptionHandler {

    @ExceptionHandler(SentenceFeedbackNotFoundException.class)
    public ResponseEntity<SentenceFeedbackErrorResponse> handleNotFound(SentenceFeedbackNotFoundException ex) {
        log.warn("sentence feedback not found reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new SentenceFeedbackErrorResponse(
                SentenceFeedbackErrorCode.SENTENCE_FEEDBACK_NOT_FOUND,
                "sentence feedback not found"));
    }

    @ExceptionHandler(SentenceFeedbackInvalidTransitionException.class)
    public ResponseEntity<SentenceFeedbackErrorResponse> handleInvalidTransition(
        SentenceFeedbackInvalidTransitionException ex) {
        log.warn("sentence feedback invalid transition reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new SentenceFeedbackErrorResponse(
                SentenceFeedbackErrorCode.SENTENCE_FEEDBACK_INVALID_TRANSITION,
                "sentence feedback is in a final state"));
    }

    @ExceptionHandler(SentenceFeedbackUnknownSuggestionException.class)
    public ResponseEntity<SentenceFeedbackErrorResponse> handleUnknownSuggestion(
        SentenceFeedbackUnknownSuggestionException ex) {
        log.warn("sentence feedback unknown suggestion reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new SentenceFeedbackErrorResponse(
                SentenceFeedbackErrorCode.SENTENCE_FEEDBACK_UNKNOWN_SUGGESTION,
                "suggestionId is not in the suggestions"));
    }

    @ExceptionHandler(SentenceFeedbackRateLimitedException.class)
    public ResponseEntity<SentenceFeedbackErrorResponse> handleRateLimited(
        SentenceFeedbackRateLimitedException ex) {
        log.warn("sentence feedback rate limited reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(new SentenceFeedbackErrorResponse(
                SentenceFeedbackErrorCode.SENTENCE_FEEDBACK_RATE_LIMITED,
                "sentence feedback request limit exceeded"));
    }

    @ExceptionHandler(InvalidSentenceTextException.class)
    public ResponseEntity<SentenceFeedbackErrorResponse> handleInvalidSentenceText(
        InvalidSentenceTextException ex) {
        log.warn("sentence feedback domain validation failed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new SentenceFeedbackErrorResponse(
                SentenceFeedbackErrorCode.SENTENCE_FEEDBACK_VALIDATION_FAILED,
                "sentence is invalid"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<SentenceFeedbackErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("sentence feedback request validation failed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new SentenceFeedbackErrorResponse(
                SentenceFeedbackErrorCode.SENTENCE_FEEDBACK_VALIDATION_FAILED,
                "request body is invalid"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<SentenceFeedbackErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("sentence feedback request body malformed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new SentenceFeedbackErrorResponse(
                SentenceFeedbackErrorCode.SENTENCE_FEEDBACK_VALIDATION_FAILED,
                "request body is malformed"));
    }

    /**
     * Path / body UUID parse 실패 IAE 매핑 — Controller 에서 {@code UUID.fromString} 호출.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<SentenceFeedbackErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("sentence feedback request rejected reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
            .body(new SentenceFeedbackErrorResponse(
                SentenceFeedbackErrorCode.SENTENCE_FEEDBACK_VALIDATION_FAILED,
                "request is invalid"));
    }

    /**
     * 인증 실패 — assignableTypes + HIGHEST_PRECEDENCE 덕에 본 핸들러가 우선 매핑.
     * identity ProfileExceptionHandler 패턴 정합 (도메인 누수 차단).
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<SentenceFeedbackErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        // test-reviewer H1 — 401 응답 코드 enum semantic 정합 (INTERNAL_ERROR 는 500 만).
        log.warn("unauthorized sentence feedback request reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new SentenceFeedbackErrorResponse(
                SentenceFeedbackErrorCode.UNAUTHORIZED,
                "authentication required"));
    }

    /**
     * Controller 한정 fallback — sentence-feedback 외 다른 generic advice 로 누수 차단.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<SentenceFeedbackErrorResponse> handleGeneric(Exception ex) {
        log.error("unhandled exception in sentence feedback presentation", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new SentenceFeedbackErrorResponse(
                SentenceFeedbackErrorCode.INTERNAL_ERROR,
                "internal server error"));
    }
}
