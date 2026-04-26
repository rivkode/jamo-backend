package app.backend.jamo.common.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RsaKeyPairKeyProviderTest {

    @Test
    void verification_key_strips_private_part() throws JOSEException {
        RSAKey signing = new RSAKeyGenerator(2048).keyID("k1").generate();

        RsaKeyPairKeyProvider provider = new RsaKeyPairKeyProvider(signing);

        assertThat(provider.signingKey().isPrivate()).isTrue();
        assertThat(provider.verificationKey().isPrivate()).isFalse();
        assertThat(provider.verificationKey().getKeyID()).isEqualTo("k1");
    }

    @Test
    void public_only_key_is_rejected() throws JOSEException {
        RSAKey publicOnly = new RSAKeyGenerator(2048).generate().toPublicJWK();

        assertThatThrownBy(() -> new RsaKeyPairKeyProvider(publicOnly))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private");
    }
}
