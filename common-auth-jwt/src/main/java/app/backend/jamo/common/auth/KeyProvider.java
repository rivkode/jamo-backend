package app.backend.jamo.common.auth;

import com.nimbusds.jose.jwk.RSAKey;

public interface KeyProvider {

    RSAKey signingKey();

    RSAKey verificationKey();
}
