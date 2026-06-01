package app.backend.jamo.diary.presentation.dto;

import java.util.Objects;

/**
 * audio API JSON 오류 응답 — {@code {code, message}} 공통 포맷 (raw 도메인 메시지 비노출).
 */
public record AudioErrorResponse(AudioErrorCode code, String message) {

    public AudioErrorResponse {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
