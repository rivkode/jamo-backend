package app.backend.jamo.identity.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LOCAL email/password 로그인 보호 정책.
 */
@ConfigurationProperties(prefix = "jamo.auth-login")
public record AuthLoginProperties(
        int maxFailures,
        Duration window
) {

    public AuthLoginProperties {
        if (maxFailures <= 0) {
            throw new IllegalArgumentException("maxFailures must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }
}
