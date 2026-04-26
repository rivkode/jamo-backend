package app.backend.jamo.common.auth;

import com.nimbusds.jose.jwk.RSAKey;

import java.util.Objects;

public final class RsaKeyPairKeyProvider implements KeyProvider {

    private final RSAKey signingKey;

    public RsaKeyPairKeyProvider(RSAKey signingKey) {
        Objects.requireNonNull(signingKey, "signingKey");
        if (!signingKey.isPrivate()) {
            throw new IllegalArgumentException("signing key must contain a private part");
        }
        this.signingKey = signingKey;
    }

    @Override
    public RSAKey signingKey() {
        return signingKey;
    }

    @Override
    public RSAKey verificationKey() {
        return signingKey.toPublicJWK();
    }
}
