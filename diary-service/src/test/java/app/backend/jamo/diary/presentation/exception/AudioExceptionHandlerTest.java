package app.backend.jamo.diary.presentation.exception;

import app.backend.jamo.diary.domain.exception.AudioClipNotFoundException;
import app.backend.jamo.diary.domain.exception.InvalidAudioException;
import app.backend.jamo.diary.presentation.dto.AudioErrorCode;
import app.backend.jamo.diary.presentation.dto.AudioErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AudioExceptionHandler 의 예외→HTTP/코드 매핑 단위 검증 — multipart 파싱 단계에서 던져져 @WebMvcTest 로
 * 재현이 까다로운 413(MaxUploadSize) / 400(part 누락) 분기를 POJO 직접 호출로 고정 (test-reviewer M1).
 */
class AudioExceptionHandlerTest {

    private final AudioExceptionHandler handler = new AudioExceptionHandler();

    @Test
    void max_upload_size_maps_to_413_AUDIO_TOO_LARGE() {
        ResponseEntity<AudioErrorResponse> res =
            handler.handleTooLarge(new MaxUploadSizeExceededException(25L * 1024 * 1024));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().code()).isEqualTo(AudioErrorCode.AUDIO_TOO_LARGE);
    }

    @Test
    void missing_audio_part_maps_to_400() {
        ResponseEntity<AudioErrorResponse> res =
            handler.handleBadRequest(new MissingServletRequestPartException("audio"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().code()).isEqualTo(AudioErrorCode.AUDIO_VALIDATION_FAILED);
    }

    @Test
    void not_found_maps_to_404() {
        ResponseEntity<AudioErrorResponse> res =
            handler.handleNotFound(new AudioClipNotFoundException("x"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody().code()).isEqualTo(AudioErrorCode.AUDIO_NOT_FOUND);
    }

    @Test
    void invalid_audio_maps_to_400() {
        ResponseEntity<AudioErrorResponse> res =
            handler.handleInvalid(new InvalidAudioException("bad"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().code()).isEqualTo(AudioErrorCode.AUDIO_VALIDATION_FAILED);
    }

    @Test
    void error_body_never_leaks_raw_message() {
        ResponseEntity<AudioErrorResponse> res =
            handler.handleInvalid(new InvalidAudioException("super secret internal path /etc/x"));
        // generic 메시지만 — raw 도메인 메시지 비노출
        assertThat(res.getBody().message()).doesNotContain("secret", "/etc/x");
    }
}
