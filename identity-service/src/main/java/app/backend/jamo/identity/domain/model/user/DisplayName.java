package app.backend.jamo.identity.domain.model.user;

import java.util.Objects;

public record DisplayName(String value) {

    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 32;

    public DisplayName {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("displayName length out of range");
        }
    }
}
