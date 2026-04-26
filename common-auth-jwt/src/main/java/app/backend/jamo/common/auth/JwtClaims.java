package app.backend.jamo.common.auth;

import java.time.Instant;
import java.util.Objects;

public record JwtClaims(
        String subject,
        String sessionId,
        String deviceId,
        JwtTokenType tokenType,
        Instant issuedAt,
        Instant expiresAt
) {
    public JwtClaims {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(tokenType, "tokenType");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }
}
