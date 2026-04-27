package app.backend.jamo.identity.infrastructure.config;

import app.backend.jamo.common.auth.BlacklistChecker;
import app.backend.jamo.common.auth.JwtIssuer;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.common.auth.KeyProvider;
import app.backend.jamo.common.auth.RsaJwtIssuer;
import app.backend.jamo.common.auth.RsaJwtVerifier;
import app.backend.jamo.common.auth.RsaKeyPairKeyProvider;
import app.backend.jamo.identity.domain.model.auth.AuthorizationCodeGenerator;
import app.backend.jamo.identity.domain.repository.SessionBlacklist;
import app.backend.jamo.identity.domain.service.RefreshTokenHasher;
import app.backend.jamo.identity.domain.service.SessionIdGenerator;
import app.backend.jamo.identity.infrastructure.security.HmacRefreshTokenHasher;
import app.backend.jamo.identity.infrastructure.security.UuidSessionIdGenerator;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties({
        JwtProperties.class,
        OAuthProviderProperties.class,
        RefreshTokenHashProperties.class,
        DeviceCookieProperties.class,
        FrontendProperties.class,
        EmailValidationProperties.class
})
public class IdentityServiceConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    public AuthorizationCodeGenerator authorizationCodeGenerator(SecureRandom random) {
        return new AuthorizationCodeGenerator(random);
    }

    @Bean
    @ConditionalOnMissingBean(KeyProvider.class)
    public KeyProvider jwtKeyProvider(JwtProperties props) {
        try {
            RSAKey key = JwkPemReader.readSigningKey(props.privateKeyPem(), props.publicKeyPem(), props.keyId());
            return new RsaKeyPairKeyProvider(key);
        } catch (JOSEException | ParseException e) {
            throw new IllegalStateException("failed to load RSA signing key from configuration", e);
        }
    }

    @Bean
    public JwtIssuer jwtIssuer(KeyProvider keyProvider, JwtProperties props) {
        return new RsaJwtIssuer(keyProvider, props.issuer(), props.audience());
    }

    @Bean
    public JwtVerifier jwtVerifier(KeyProvider keyProvider, JwtProperties props,
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

    /**
     * common-auth-jwt 의 {@link BlacklistChecker} 어댑터 — JWT verify hot path 에서
     * {@link SessionBlacklist#contains} 를 호출해 logout/reuse-detection 등록 sid 를 즉시 거부.
     */
    @Bean
    public BlacklistChecker blacklistChecker(SessionBlacklist sessionBlacklist) {
        return sessionBlacklist::contains;
    }

    @Bean
    public RefreshTokenHasher refreshTokenHasher(RefreshTokenHashProperties properties) {
        return new HmacRefreshTokenHasher(properties);
    }

    @Bean
    public SessionIdGenerator sessionIdGenerator() {
        return new UuidSessionIdGenerator();
    }
}
