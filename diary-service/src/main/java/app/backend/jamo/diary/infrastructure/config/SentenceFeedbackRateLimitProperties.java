package app.backend.jamo.diary.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * sentence-feedback rate limit 설정.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §11 (분 10 / 일 50회).
 *
 * <p>운영 envvar 로 재정의 가능 (application.yaml 의 SENTENCE_FEEDBACK_*).
 *
 * @param cooldown    분당 cooldown 윈도우 (default 60s)
 * @param minuteLimit cooldown 윈도우 내 호출 한도 (cooldown 위반 시 즉시 거부)
 * @param dailyLimit  일일 호출 한도 (24h 윈도우)
 */
@ConfigurationProperties(prefix = "jamo.sentence-feedback.rate-limit")
public record SentenceFeedbackRateLimitProperties(
    Duration cooldown,
    int minuteLimit,
    int dailyLimit
) {
    public SentenceFeedbackRateLimitProperties {
        if (cooldown == null || cooldown.isNegative() || cooldown.isZero()) {
            throw new IllegalArgumentException("cooldown must be positive");
        }
        if (minuteLimit <= 0) {
            throw new IllegalArgumentException("minuteLimit must be positive");
        }
        if (dailyLimit <= 0) {
            throw new IllegalArgumentException("dailyLimit must be positive");
        }
    }
}
