package app.backend.jamo.diary.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * diary-service 의 JWT verify-only 설정.
 *
 * <p>identity-service `JwtProperties` (전체 issuer/audience/keyId/private+public/access+refresh TTL/clockSkew)
 * 와 같은 envvar prefix `jamo.jwt.*` 를 공유하지만, diary-service 는 token 검증만 수행하므로
 * **publicKeyPem / issuer / audience / keyId / clockSkew 만 필요**. privateKeyPem / accessTtl / refreshTtl 는
 * 발급자 (identity-service) 전용 — 본 record 미포함.
 *
 * <p>박제: decisions/diary/sentence-feedback-presentation-decisions.md (security-reviewer C1 — JWT verify Bean
 * 그래프 도입). common-auth-jwt 모듈로 verify-only properties 추출은 두 번째 도메인 controller PR 에서.
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
            throw new IllegalStateException(
                "JWT public key material is missing. Set JWT_PUBLIC_KEY_PEM."
            );
        }
        if (clockSkew == null || clockSkew.isNegative()) {
            throw new IllegalArgumentException("clockSkew must be zero or positive");
        }
    }
}
