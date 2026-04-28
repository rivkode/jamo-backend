package app.backend.jamo.identity.presentation.dto;

import java.util.Objects;

/**
 * Profile API 의 JSON 오류 응답 표준 — code + generic message 만.
 *
 * <p>도메인 예외의 raw message / cause stack 은 클라이언트에 노출 금지 (User 도메인 정합).
 */
public record ProfileErrorResponse(ProfileErrorCode code, String message) {

    public ProfileErrorResponse {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
