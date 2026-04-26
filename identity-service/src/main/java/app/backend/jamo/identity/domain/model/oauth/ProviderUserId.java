package app.backend.jamo.identity.domain.model.oauth;

import java.util.Objects;

public record ProviderUserId(String value) {

    public static final int MAX_LENGTH = 128;

    public ProviderUserId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("providerUserId must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("providerUserId too long");
        }
    }
}
