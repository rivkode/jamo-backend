package app.backend.jamo.identity.domain.model.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

public final class AuthorizationCodeGenerator {

    private static final int CODE_BYTES = 32;

    private final SecureRandom random;

    public AuthorizationCodeGenerator(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public String generate() {
        byte[] buffer = new byte[CODE_BYTES];
        random.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }
}
