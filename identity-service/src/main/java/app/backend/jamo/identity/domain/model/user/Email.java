package app.backend.jamo.identity.domain.model.user;

import java.util.Objects;
import java.util.regex.Pattern;

public record Email(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MAX_LENGTH = 254;

    public Email {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("email too long");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid email format");
        }
    }
}
