package app.backend.jamo.diary.presentation.exception;

import app.backend.jamo.diary.domain.exception.AudioClipNotFoundException;
import app.backend.jamo.diary.domain.exception.InvalidAudioException;
import app.backend.jamo.diary.presentation.controller.AudioController;
import app.backend.jamo.diary.presentation.controller.AudioServeController;
import app.backend.jamo.diary.presentation.dto.AudioErrorCode;
import app.backend.jamo.diary.presentation.dto.AudioErrorResponse;
import app.backend.jamo.diary.presentation.web.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * audio sub-domain presentation ExceptionHandler.
 *
 * <p>{@code assignableTypes = {AudioController, AudioServeController}} 한정 + HIGHEST_PRECEDENCE — 같은
 * diary-service 내 다른 sub-domain ExceptionHandler 와 매핑 충돌 회피 (DiaryExceptionHandler 패턴 정합).
 *
 * <p>매핑:
 * <ul>
 *   <li>{@link AudioClipNotFoundException} → 404 AUDIO_NOT_FOUND</li>
 *   <li>{@link InvalidAudioException} → 400 AUDIO_VALIDATION_FAILED (빈 본문 / content-type / 크기 초과)</li>
 *   <li>{@link MaxUploadSizeExceededException} → 413 AUDIO_TOO_LARGE (multipart 한도)</li>
 *   <li>{@link MissingServletRequestPartException} → 400 ({@code audio} 파트 누락)</li>
 *   <li>{@link IllegalArgumentException} → 400 (파일명 형식 위반 등)</li>
 *   <li>{@link UnauthorizedException} → 401</li>
 *   <li>{@link Exception} fallback → 500 (sanitize)</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice(assignableTypes = {AudioController.class, AudioServeController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AudioExceptionHandler {

    @ExceptionHandler(AudioClipNotFoundException.class)
    public ResponseEntity<AudioErrorResponse> handleNotFound(AudioClipNotFoundException ex) {
        log.warn("audio not found reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new AudioErrorResponse(AudioErrorCode.AUDIO_NOT_FOUND, "audio not found"));
    }

    @ExceptionHandler(InvalidAudioException.class)
    public ResponseEntity<AudioErrorResponse> handleInvalid(InvalidAudioException ex) {
        log.warn("invalid audio reason={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new AudioErrorResponse(AudioErrorCode.AUDIO_VALIDATION_FAILED, "invalid audio"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<AudioErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("audio upload too large");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(new AudioErrorResponse(AudioErrorCode.AUDIO_TOO_LARGE, "audio too large"));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, IllegalArgumentException.class})
    public ResponseEntity<AudioErrorResponse> handleBadRequest(Exception ex) {
        log.warn("audio bad request reason={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new AudioErrorResponse(AudioErrorCode.AUDIO_VALIDATION_FAILED, "invalid request"));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<AudioErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        log.warn("audio unauthorized");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new AudioErrorResponse(AudioErrorCode.UNAUTHORIZED, "unauthorized"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AudioErrorResponse> handleInternal(Exception ex) {
        log.error("audio internal error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new AudioErrorResponse(AudioErrorCode.INTERNAL_ERROR, "internal error"));
    }
}
