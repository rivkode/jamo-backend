package app.backend.jamo.identity.domain.model.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

public record PkceVerifier(String value) {

    public static final int MIN_LENGTH = 43;
    public static final int MAX_LENGTH = 128;
    private static final int RANDOM_BYTES = 32;

    public PkceVerifier {
        Objects.requireNonNull(value, "value");
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("PKCE verifier length out of range");
        }
    }

    public static PkceVerifier random(SecureRandom random) {
        Objects.requireNonNull(random, "random");
        byte[] buffer = new byte[RANDOM_BYTES];
        random.nextBytes(buffer);
        return new PkceVerifier(Base64.getUrlEncoder().withoutPadding().encodeToString(buffer));
    }

    public PkceChallenge challenge() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(value.getBytes(StandardCharsets.US_ASCII));
            return new PkceChallenge(Base64.getUrlEncoder().withoutPadding().encodeToString(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
