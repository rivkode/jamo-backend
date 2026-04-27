package app.backend.jamo.identity.e2e;

import app.backend.jamo.common.auth.KeyProvider;
import app.backend.jamo.common.auth.RsaKeyPairKeyProvider;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * E2E 전용 RSA key 설정. 매 test class 시작 시 random RSA-2048 keypair 동적 생성 →
 * production 의 PEM 파일 의존성 제거. {@link KeyProvider} 빈을 IdentityServiceConfig 의
 * {@code @ConditionalOnMissingBean} 보다 먼저 등록해 production Bean 을 덮어쓴다.
 */
@TestConfiguration
public class TestKeyConfig {

    @Bean
    public KeyProvider testKeyProvider() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID("test-kid")
                    .build();
            return new RsaKeyPairKeyProvider(rsaKey);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available in test JVM", e);
        }
    }
}
