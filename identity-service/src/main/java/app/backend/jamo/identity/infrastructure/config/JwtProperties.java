package app.backend.jamo.identity.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "jamo.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        String keyId,
        String privateKeyPem,
        String publicKeyPem,
        Duration accessTtl,
        Duration refreshTtl,
        Duration clockSkew
) {
    /**
     * sid blacklist 등록 시 사용할 TTL — access JWT 만료 시간 + clockSkew leeway.
     *
     * <p>{@code RsaJwtVerifier} 가 exp+clockSkew 까지 토큰을 유효로 보므로 blacklist 도
     * 동일 윈도우를 커버해야 logout/회전 직후 clockSkew 안의 access JWT 가 통과되는
     * 보안 boundary 약화를 막는다.
     */
    public Duration blacklistTtl() {
        return accessTtl.plus(clockSkew);
    }

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("issuer must not be blank");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("audience must not be blank");
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId must not be blank");
        }
        if (accessTtl == null || accessTtl.isZero() || accessTtl.isNegative()) {
            throw new IllegalArgumentException("accessTtl must be positive");
        }
        if (refreshTtl == null || refreshTtl.isZero() || refreshTtl.isNegative()) {
            throw new IllegalArgumentException("refreshTtl must be positive");
        }
        if (clockSkew == null || clockSkew.isNegative()) {
            throw new IllegalArgumentException("clockSkew must be zero or positive");
        }
    }
}
