package app.backend.jamo.identity.domain.model.auth;

import java.util.Objects;
import java.util.UUID;

public record AuthState(String value) {

    public AuthState {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("state must not be blank");
        }
    }

    public static AuthState random() {
        return new AuthState(UUID.randomUUID().toString());
    }
}
