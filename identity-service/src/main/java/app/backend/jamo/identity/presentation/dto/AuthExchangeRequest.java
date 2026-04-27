package app.backend.jamo.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/v1/auth/exchange 의 Request body.
 * code 는 OAuth callback 응답으로 받은 일회성 authorization code.
 */
public record AuthExchangeRequest(
        @NotBlank(message = "code must not be blank") String code
) {
}
