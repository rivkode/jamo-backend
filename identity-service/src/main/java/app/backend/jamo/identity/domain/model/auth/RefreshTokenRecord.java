package app.backend.jamo.identity.domain.model.auth;

import app.backend.jamo.identity.domain.model.user.UserId;

import java.time.Instant;
import java.util.Objects;

public record RefreshTokenRecord(
        UserId userId,
        String sessionId,
        String deviceId,
        String tokenHash,
        Instant issuedAt,
        Instant expiresAt
) {
    public RefreshTokenRecord {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(sessionId, "sessionId");
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        Objects.requireNonNull(deviceId, "deviceId");
        if (deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
        Objects.requireNonNull(tokenHash, "tokenHash");
        if (tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash must not be blank");
        }
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }
}
