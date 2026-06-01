package app.backend.jamo.chat.presentation.dto;

import java.util.Objects;

/** chat-service JSON 오류 응답 — {@code {code, message}} 공통 포맷 (raw 비노출). */
public record ChatErrorResponse(ChatErrorCode code, String message) {

    public ChatErrorResponse {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
