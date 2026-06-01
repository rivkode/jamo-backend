package app.backend.jamo.diary.presentation.exception;

import app.backend.jamo.diary.domain.exception.ChatRoomForbiddenException;
import app.backend.jamo.diary.domain.exception.ChatRoomNotFoundException;
import app.backend.jamo.diary.domain.exception.InvalidChatMessageException;
import app.backend.jamo.diary.presentation.controller.DiaryChatMessageController;
import app.backend.jamo.diary.presentation.controller.DiaryChatRoomController;
import app.backend.jamo.diary.presentation.dto.diarychat.ChatErrorCode;
import app.backend.jamo.diary.presentation.dto.diarychat.ChatErrorResponse;
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
 * diarychat presentation ExceptionHandler — {@code assignableTypes = DiaryChatRoomController} 한정 +
 * HIGHEST_PRECEDENCE (다른 sub-domain 핸들러와 매핑 충돌 회피, DiaryExceptionHandler 패턴 정합).
 *
 * <p>매핑 (v2 §2):
 * <ul>
 *   <li>{@link ChatRoomNotFoundException} → 404 (방 부재 / 비공개 비작성자 / 비참여 IDOR)</li>
 *   <li>{@link ChatRoomForbiddenException} → 403 (ai-toggle 비호스트)</li>
 *   <li>Bean Validation / malformed body / type mismatch / {@link IllegalArgumentException} → 400</li>
 *   <li>{@link UnauthorizedException} → 401</li>
 *   <li>{@link Exception} fallback → 500 (sanitize)</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice(assignableTypes = {DiaryChatRoomController.class, DiaryChatMessageController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DiaryChatExceptionHandler {

    @ExceptionHandler(ChatRoomNotFoundException.class)
    public ResponseEntity<ChatErrorResponse> handleNotFound(ChatRoomNotFoundException ex) {
        log.warn("chat room not found reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ChatErrorResponse(ChatErrorCode.CHAT_ROOM_NOT_FOUND, "chat room not found"));
    }

    @ExceptionHandler(ChatRoomForbiddenException.class)
    public ResponseEntity<ChatErrorResponse> handleForbidden(ChatRoomForbiddenException ex) {
        log.warn("chat room forbidden reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ChatErrorResponse(ChatErrorCode.CHAT_ROOM_FORBIDDEN, "forbidden"));
    }

    @ExceptionHandler({
        InvalidChatMessageException.class,
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
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
