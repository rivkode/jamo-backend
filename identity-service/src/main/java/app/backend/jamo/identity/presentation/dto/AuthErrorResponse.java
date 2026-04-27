package app.backend.jamo.identity.presentation.dto;

import java.util.Objects;

/**
 * Exchange API 의 JSON 오류 응답 표준.
 * Callback 의 redirect URL 에는 {@link AuthErrorCode} 만 query param 으로 노출.
 */
public record AuthErrorResponse(AuthErrorCode code, String message) {

    public AuthErrorResponse {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
