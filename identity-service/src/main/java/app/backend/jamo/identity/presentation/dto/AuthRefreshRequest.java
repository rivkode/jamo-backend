package app.backend.jamo.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/v1/auth/refresh 의 Request body.
 * refreshToken 은 OAuth exchange 또는 이전 refresh 회전으로 발급된 refresh JWT.
 */
public record AuthRefreshRequest(
        @NotBlank(message = "refreshToken must not be blank") String refreshToken
) {
}
