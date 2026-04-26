package app.backend.jamo.identity.domain.model.auth;

import java.util.Objects;

public record PkceChallenge(String value) {

    public PkceChallenge {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("PKCE challenge must not be blank");
        }
    }

    public boolean matches(PkceVerifier verifier) {
        Objects.requireNonNull(verifier, "verifier");
        return this.equals(verifier.challenge());
    }
}
