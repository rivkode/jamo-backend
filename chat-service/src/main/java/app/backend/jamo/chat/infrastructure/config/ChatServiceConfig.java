package app.backend.jamo.chat.infrastructure.config;

import app.backend.jamo.common.auth.BlacklistChecker;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.common.auth.KeyProvider;
import app.backend.jamo.common.auth.RsaJwtVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * chat-service infrastructure bean — JWT verify-only 그래프 + Clock. diary-service 정합 (DB 없음 — gateway).
 *
 * <p>BlacklistChecker 는 common-infrastructure 의 SessionBlacklist(Redis) 어댑터 자동 주입.
 */
@Configuration
@EnableConfigurationProperties({
    JwtVerifierProperties.class,
    CorsProperties.class,
    AudioStorageProperties.class
})
public class ChatServiceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public KeyProvider jwtVerifierKeyProvider(JwtVerifierProperties props) {
        RSAKey verificationKey = JwkPemReader.readVerificationKey(props.publicKeyPem(), props.keyId());
        return new KeyProvider() {
            @Override
            public RSAKey signingKey() {
                throw new UnsupportedOperationException("chat-service is verify-only");
            }

            @Override
            public RSAKey verificationKey() {
                return verificationKey;
            }
        };
    }

    @Bean
    public JwtVerifier jwtVerifier(KeyProvider keyProvider, JwtVerifierProperties props,
                                   BlacklistChecker blacklistChecker, Clock clock) {
        return new RsaJwtVerifier(
            keyProvider,
            props.issuer(),
            props.audience(),
            blacklistChecker,
            clock,
            props.clockSkew()
        );
    }
}
