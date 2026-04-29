package app.backend.jamo.diary.infrastructure.redis;

import app.backend.jamo.diary.domain.repository.SentenceFeedbackRateLimiter;
import app.backend.jamo.diary.infrastructure.config.SentenceFeedbackRateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * {@link SentenceFeedbackRateLimiter} 의 Redis 구현 — identity 의 {@code ValidationRateLimiterRedisStore}
 * 패턴 정합 (cooldown + daily counter).
 *
 * <p>두 키 그룹:
 * <ul>
 *   <li>{@code user:sentence-feedback:cooldown:{userId}} — minute 윈도우 카운터 (INCR), TTL = cooldown.
 *       카운터가 minuteLimit 초과 시 거부.</li>
 *   <li>{@code user:sentence-feedback:daily:{userId}} — 1일 카운터 (INCR), TTL = 24h. dailyLimit 초과 시 거부.</li>
 * </ul>
 *
 * <p><b>TOCTOU race</b> (identity 정합): {@link #canRequest} 의 GET 후 {@link #recordRequest} INCR 사이
 * 다른 동시 요청이 통과 가능 (atomic CAS 아님). chat-service 호출 비용 보호 목적의 soft cap 이라
 * 분산 lock / Lua 도입은 운영 영향 측정 후 결정.
 */
@Component
@RequiredArgsConstructor
public class SentenceFeedbackRateLimiterRedisStore implements SentenceFeedbackRateLimiter {

    private static final String COOLDOWN_KEY_PREFIX = "user:sentence-feedback:cooldown:";
    private static final String DAILY_KEY_PREFIX = "user:sentence-feedback:daily:";
    private static final Duration DAILY_TTL = Duration.ofDays(1);

    private final StringRedisTemplate redis;
    private final SentenceFeedbackRateLimitProperties properties;

    @Override
    public boolean canRequest(UUID userId) {
        String cooldownStr = redis.opsForValue().get(cooldownKey(userId));
        int cooldownCount = cooldownStr == null ? 0 : Integer.parseInt(cooldownStr);
        if (cooldownCount >= properties.minuteLimit()) {
            return false;
        }
        String dailyStr = redis.opsForValue().get(dailyKey(userId));
        int daily = dailyStr == null ? 0 : Integer.parseInt(dailyStr);
        return daily < properties.dailyLimit();
    }

    @Override
    public void recordRequest(UUID userId) {
        Long cooldownAfter = redis.opsForValue().increment(cooldownKey(userId));
        if (cooldownAfter != null && cooldownAfter == 1L) {
            // 첫 INCR 시점에만 TTL 설정 — sliding window 회피 (cooldown 윈도우 = fixed)
            redis.expire(cooldownKey(userId), properties.cooldown());
        }
        Long dailyAfter = redis.opsForValue().increment(dailyKey(userId));
        if (dailyAfter != null && dailyAfter == 1L) {
            redis.expire(dailyKey(userId), DAILY_TTL);
        }
    }

    private String cooldownKey(UUID userId) {
        return COOLDOWN_KEY_PREFIX + userId;
    }

    private String dailyKey(UUID userId) {
        return DAILY_KEY_PREFIX + userId;
    }
}
