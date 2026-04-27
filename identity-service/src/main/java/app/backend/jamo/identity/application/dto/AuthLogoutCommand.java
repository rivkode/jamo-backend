package app.backend.jamo.identity.application.dto;

import app.backend.jamo.identity.domain.model.user.UserId;

import java.util.Objects;

public record AuthLogoutCommand(UserId userId, String sessionId) {

    public AuthLogoutCommand {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(sessionId, "sessionId");
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
    }
}
