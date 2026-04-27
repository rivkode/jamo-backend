package app.backend.jamo.identity.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * Device 식별 cookie 의 속성 (decisions/auth/cookie-policy.md).
 *
 * <p>Path 는 항상 {@code /} (deviceId 가 모든 인증 흐름에서 사용). Name 은 항상
 * {@code jamo_device_id}. 본 record 는 운영 환경에 따라 달라지는 속성만 보유.
 */
@ConfigurationProperties(prefix = "jamo.device-cookie")
public record DeviceCookieProperties(
        String domain,
        boolean secure,
        String sameSite,
        Duration maxAge
) {
    public DeviceCookieProperties {
        Objects.requireNonNull(sameSite, "sameSite");
        if (sameSite.isBlank()) {
            throw new IllegalArgumentException("sameSite must not be blank");
        }
        Objects.requireNonNull(maxAge, "maxAge");
        if (maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException("maxAge must be positive");
        }
    }
}
