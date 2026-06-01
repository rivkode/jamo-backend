package app.backend.jamo.chat.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * diarychat AI 자동응답 rate limit 설정 — 사용자 단위 최소 가드 (S4).
 *
 * @param maxPerWindow 윈도우당 최대 호출 수 (사용자 1인)
 * @param window       고정 윈도우 길이
 */
@ConfigurationProperties(prefix = "jamo.ai.rate-limit")
public record AiRateLimitProperties(int maxPerWindow, Duration window) {

    public AiRateLimitProperties {
        if (maxPerWindow <= 0) {
            maxPerWindow = 20;
        }
        if (window == null || window.isZero() || window.isNegative()) {
            window = Duration.ofMinutes(1);
        }
    }
}
