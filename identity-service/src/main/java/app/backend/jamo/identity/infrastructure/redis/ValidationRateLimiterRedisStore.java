package app.backend.jamo.identity.infrastructure.redis;

import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.repository.ValidationRateLimiter;
import app.backend.jamo.identity.infrastructure.config.EmailValidationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@link ValidationRateLimiter} 의 Redis 구현 — 두 키 그룹 (PRD §9).
 *
 * <ul>
 *   <li>{@code user:validation:cooldown:{email}} — 30초 발송 간격 락. TTL = {@code cooldown}.
 *       존재하면 발송 거부.</li>
 *   <li>{@code user:validation:daily:{email}} — 1일 발송 카운터 (INCR). TTL = 24h. 한도 도달 시 거부.</li>
 * </ul>
 *
 * <p>{@link #canSend} 는 read-only. {@link #recordSend} 는 cooldown SET + daily INCR — 두 키
 * atomic 아니지만 부분 실패 영향 작음 (cooldown 만 set 되고 daily INCR 실패 시 spam 가능성
 * 미세 증가, daily INCR 만 되고 cooldown 실패 시 30s 윈도우 약화).
 *
 * <p><b>TOCTOU race</b>: {@link #canSend} 의 daily 카운터 GET 후 {@link #recordSend} INCR 사이에
 * 다른 동시 요청이 통과 가능 (atomic CAS 아님). 그러나 cooldown 30s 가 1차 방어선이고 daily
 * 한도는 best-effort soft cap — 단일 email 동시 다중 요청 시 cooldown 으로 한 요청만 통과,
 * 나머지는 즉시 거부. 분산 lock / Lua 도입은 운영 영향 측정 후 결정.
 */
@Component
public class ValidationRateLimiterRedisStore implements ValidationRateLimiter {

    private static final String COOLDOWN_KEY_PREFIX = "user:validation:cooldown:";
    private static final String DAILY_KEY_PREFIX = "user:validation:daily:";
    private static final String VALUE = "1";
    private static final Duration DAILY_TTL = Duration.ofDays(1);

    private final StringRedisTemplate redis;
    private final EmailValidationProperties properties;

    public ValidationRateLimiterRedisStore(StringRedisTemplate redis,
                                           EmailValidationProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public boolean canSend(Email email) {
        if (Boolean.TRUE.equals(redis.hasKey(cooldownKey(email)))) {
            return false;
        }
        String dailyStr = redis.opsForValue().get(dailyKey(email));
        int daily = dailyStr == null ? 0 : Integer.parseInt(dailyStr);
        return daily < properties.dailyLimit();
    }

    @Override
    public void recordSend(Email email) {
        redis.opsForValue().set(cooldownKey(email), VALUE, properties.cooldown());
        redis.opsForValue().increment(dailyKey(email));
        redis.expire(dailyKey(email), DAILY_TTL);
    }

    private String cooldownKey(Email email) {
        return COOLDOWN_KEY_PREFIX + email.value();
    }

    private String dailyKey(Email email) {
        return DAILY_KEY_PREFIX + email.value();
    }
}
