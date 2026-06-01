package app.backend.jamo.chat.infrastructure.redis;

import app.backend.jamo.common.auth.BlacklistChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link BlacklistChecker} Redis 구현 — identity-service 의 `bl:sid:{sessionId}` 키 schema 공유 (read-only EXISTS).
 * diary-service {@code SessionBlacklistRedisChecker} 정합 — 키 schema 변경 시 양 서비스 동시 변경.
 */
@Component
@RequiredArgsConstructor
public class SessionBlacklistRedisChecker implements BlacklistChecker {

    private static final String KEY_PREFIX = "bl:sid:";

    private final StringRedisTemplate redis;

    @Override
    public boolean isBlacklisted(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + sessionId));
    }
}
