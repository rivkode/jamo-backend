package app.backend.jamo.identity.application.dto;

import java.util.Objects;

public record AuthExchangeCommand(String code) {

    public AuthExchangeCommand {
        Objects.requireNonNull(code, "code");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }
}
