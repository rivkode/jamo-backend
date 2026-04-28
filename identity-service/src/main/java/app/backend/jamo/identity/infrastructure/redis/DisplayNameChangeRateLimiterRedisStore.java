package app.backend.jamo.identity.infrastructure.redis;

import app.backend.jamo.identity.domain.exception.DisplayNameChangeTooFrequentException;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.DisplayNameChangeRateLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@link DisplayNameChangeRateLimiter} 의 Redis 구현.
 *
 * <p>KEY: {@code user:displayName_changed:{userId}} — User SoT 정합 (decisions/identity/profile-app-infra-decisions.md).
 *
 * <p><b>호출 시점</b>:
 * <ul>
 *   <li>{@link #check}: {@code UpdateMyProfileService} 의 트랜잭션 내부에서 사용자 displayName 변경 직전.</li>
 *   <li>{@link #markChanged}: {@code DisplayNameChangeRateLimiterListener} 가 {@code @TransactionalEventListener(AFTER_COMMIT)}
 *       으로 commit 후 호출. RDB rollback 시 본 메서드 미호출 → flag 미반영 (정합성 안전).</li>
 * </ul>
 */
@Component
public class DisplayNameChangeRateLimiterRedisStore implements DisplayNameChangeRateLimiter {

    private static final String KEY_PREFIX = "user:displayName_changed:";
    private static final String VALUE = "1";

    private final StringRedisTemplate redis;

    public DisplayNameChangeRateLimiterRedisStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void check(UserId userId) {
        Boolean exists = redis.hasKey(key(userId));
        if (Boolean.TRUE.equals(exists)) {
            throw new DisplayNameChangeTooFrequentException(
                    "displayName change rate limit hit (7 days)");
        }
    }

    @Override
    public void markChanged(UserId userId, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        redis.opsForValue().set(key(userId), VALUE, ttl);
    }

    private String key(UserId userId) {
        return KEY_PREFIX + userId.value();
    }
}
