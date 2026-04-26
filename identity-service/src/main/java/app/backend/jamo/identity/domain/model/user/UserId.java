package app.backend.jamo.identity.domain.model.user;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value");
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId fromString(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new UserId(UUID.fromString(raw));
    }

    public String asString() {
        return value.toString();
    }
}
