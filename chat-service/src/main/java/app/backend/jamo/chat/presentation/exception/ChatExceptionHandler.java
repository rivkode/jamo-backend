package app.backend.jamo.chat.presentation.exception;

import app.backend.jamo.chat.domain.ai.AiUnavailableException;
import app.backend.jamo.chat.presentation.controller.AudioServeController;
import app.backend.jamo.chat.presentation.controller.ChatController;
import app.backend.jamo.chat.presentation.dto.ChatErrorCode;
import app.backend.jamo.chat.presentation.dto.ChatErrorResponse;
import app.backend.jamo.chat.presentation.web.UnauthorizedException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * chat-service presentation ExceptionHandler — {ChatController, AudioServeController} 한정.
 *
 * <ul>
 *   <li>{@link AiUnavailableException} → 503 (ai-service 장애 / Circuit open)</li>
 *   <li>{@link AudioServeController.AudioNotFoundException} → 404</li>
 *   <li>{@link MaxUploadSizeExceededException} → 413</li>
 *   <li>검증/malformed/part 누락/{@link IllegalArgumentException} → 400</li>
 *   <li>{@link UnauthorizedException} → 401</li>
 *   <li>{@link Exception} → 500 (sanitize)</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice(assignableTypes = {ChatController.class, AudioServeController.class})
public class ChatExceptionHandler {

    @ExceptionHandler(AiUnavailableException.class)
    public ResponseEntity<ChatErrorResponse> handleAiUnavailable(AiUnavailableException ex) {
        log.warn("ai-service unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ChatErrorResponse(ChatErrorCode.AI_UNAVAILABLE, "ai service temporarily unavailable"));
    }

    @ExceptionHandler(AudioServeController.AudioNotFoundException.class)
    public ResponseEntity<ChatErrorResponse> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ChatErrorResponse(ChatErrorCode.AUDIO_NOT_FOUND, "audio not found"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ChatErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(new ChatErrorResponse(ChatErrorCode.AUDIO_TOO_LARGE, "audio too large"));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestPartException.class,
        IllegalArgumentException.class
    })
    public ResponseEntity<ChatErrorResponse> handleValidation(Exception ex) {
        log.warn("chat validation failed reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ChatErrorResponse(ChatErrorCode.CHAT_VALIDATION_FAILED, "invalid request"));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ChatErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        log.warn("chat unauthorized");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ChatErrorResponse(ChatErrorCode.UNAUTHORIZED, "unauthorized"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ChatErrorResponse> handleInternal(Exception ex) {
        log.error("chat internal error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ChatErrorResponse(ChatErrorCode.INTERNAL_ERROR, "internal error"));
    }
}
