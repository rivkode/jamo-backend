package app.backend.jamo.common.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RsaJwtIssuerTest {

    private static KeyProvider keyProvider;

    @BeforeAll
    static void generateKey() throws JOSEException {
        RSAKey key = new RSAKeyGenerator(2048).keyID("k1").generate();
        keyProvider = new RsaKeyPairKeyProvider(key);
    }

    @Test
    void blank_issuer_is_rejected() {
        assertThatThrownBy(() -> new RsaJwtIssuer(keyProvider, " ", "aud"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void blank_audience_is_rejected() {
        assertThatThrownBy(() -> new RsaJwtIssuer(keyProvider, "iss", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void null_key_provider_is_rejected() {
        assertThatThrownBy(() -> new RsaJwtIssuer(null, "iss", "aud"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("keyProvider");
    }
}
