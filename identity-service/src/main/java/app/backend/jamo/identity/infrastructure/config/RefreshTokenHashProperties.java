package app.backend.jamo.identity.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

/**
 * Refresh JWT 의 hash 계산용 pepper (decisions/auth/refresh-token-hash.md).
 *
 * <p>HMAC-SHA256 의 key 강도는 ≥ 256 bits 권장 — 32자 minimum 강제.
 * 환경변수 {@code JWT_REFRESH_HASH_PEPPER} 미설정 시 fail-fast (운영 misconfig 차단).
 */
@ConfigurationProperties(prefix = "jamo.refresh-token-hash")
public record RefreshTokenHashProperties(String pepper) {

    public static final int MIN_PEPPER_LENGTH = 32;

    public RefreshTokenHashProperties {
        Objects.requireNonNull(pepper, "pepper");
        if (pepper.isBlank()) {
            throw new IllegalArgumentException(
                    "refresh-token-hash pepper must not be blank — set JWT_REFRESH_HASH_PEPPER env var");
        }
        if (pepper.length() < MIN_PEPPER_LENGTH) {
            throw new IllegalArgumentException(
                    "refresh-token-hash pepper must be at least " + MIN_PEPPER_LENGTH
                            + " chars (256 bits) for HMAC-SHA256");
        }
    }
}
