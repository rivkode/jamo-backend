package app.backend.jamo.identity.application.dto;

import app.backend.jamo.identity.domain.model.user.UserId;

import java.util.Objects;

public record AuthExchangeResult(
        UserId userId,
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {
    public AuthExchangeResult {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(accessToken, "accessToken");
        if (accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
        Objects.requireNonNull(refreshToken, "refreshToken");
        if (refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken must not be blank");
        }
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("expiresInSeconds must be positive");
        }
    }
}
