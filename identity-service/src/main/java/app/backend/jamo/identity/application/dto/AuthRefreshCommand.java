package app.backend.jamo.identity.application.dto;

import java.util.Objects;

public record AuthRefreshCommand(String refreshTokenJwt) {

    public AuthRefreshCommand {
        Objects.requireNonNull(refreshTokenJwt, "refreshTokenJwt");
        if (refreshTokenJwt.isBlank()) {
            throw new IllegalArgumentException("refreshTokenJwt must not be blank");
        }
    }
}
