package app.backend.jamo.identity.presentation.dto;

import java.util.Objects;

/**
 * User API 의 JSON 오류 응답 표준 — code + generic message 만.
 *
 * <p>도메인 예외의 raw message / cause stack 은 절대 클라이언트에 노출하지 않는다
 * (PR5-b security review H2). 잔여/누적 시도 횟수도 응답에 포함 금지.
 */
public record UserErrorResponse(UserErrorCode code, String message) {

    public UserErrorResponse {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
