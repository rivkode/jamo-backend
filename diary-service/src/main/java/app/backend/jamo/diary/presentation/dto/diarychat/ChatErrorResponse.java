package app.backend.jamo.diary.presentation.dto.diarychat;

import java.util.Objects;

/**
 * diarychat API JSON 오류 응답 — {@code {code, message}} 공통 포맷 (raw 도메인 메시지 비노출).
 */
public record ChatErrorResponse(ChatErrorCode code, String message) {

    public ChatErrorResponse {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
