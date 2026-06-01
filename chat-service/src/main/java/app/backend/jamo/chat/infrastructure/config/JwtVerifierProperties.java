package app.backend.jamo.chat.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * chat-service JWT verify-only 설정 (identity 발급 access token 검증만). diary-service 정합.
 * envvar prefix {@code jamo.jwt.*} 공유, publicKeyPem/issuer/audience/keyId/clockSkew 만 필요.
 */
@ConfigurationProperties(prefix = "jamo.jwt")
public record JwtVerifierProperties(
    String issuer,
    String audience,
    String keyId,
    String publicKeyPem,
    Duration clockSkew
) {
    public JwtVerifierProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("issuer must not be blank");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("audience must not be blank");
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId must not be blank");
        }
        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            throw new IllegalStateException("JWT public key material is missing. Set JWT_PUBLIC_KEY_PEM.");
        }
        if (clockSkew == null || clockSkew.isNegative()) {
            throw new IllegalArgumentException("clockSkew must be zero or positive");
        }
    }
}
