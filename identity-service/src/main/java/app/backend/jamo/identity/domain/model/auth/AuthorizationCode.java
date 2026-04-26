package app.backend.jamo.identity.domain.model.auth;

import app.backend.jamo.identity.domain.model.user.UserId;

import java.time.Instant;
import java.util.Objects;

public record AuthorizationCode(
        String value,
        UserId userId,
        String sessionId,
        String deviceId,
        Instant issuedAt,
        Instant expiresAt
) {
    public AuthorizationCode {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("authorization code must not be blank");
        }
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(sessionId, "sessionId");
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        Objects.requireNonNull(deviceId, "deviceId");
        if (deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
