package app.backend.jamo.identity.infrastructure.redis;

import app.backend.jamo.identity.domain.repository.SessionBlacklist;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@link SessionBlacklist} 의 Redis 구현 — 개별 KEY 기반.
 *
 * <p>Redis SET 멤버 단위 TTL 은 표준 SET 으로 표현 불가하므로 sid 별 개별 KEY
 * (`bl:sid:{sid}`) + SETEX 로 구현. {@link #contains} 는 EXISTS — O(1) hot path.
 *
 * <p>JWT 만료 시 Redis 에서 자동 정리되므로 별도 cleanup job 불필요.
 */
@Component
@RequiredArgsConstructor
public class SessionBlacklistRedisStore implements SessionBlacklist {

    private static final String KEY_PREFIX = "bl:sid:";
    private static final String VALUE = "1";

    private final StringRedisTemplate redis;

    @Override
    public void blacklist(String sessionId, Duration ttl) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        redis.opsForValue().set(key(sessionId), VALUE, ttl);
    }

    @Override
    public boolean contains(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(key(sessionId)));
    }

    private String key(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
