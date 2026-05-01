package app.backend.jamo.identity.infrastructure.redis;

import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.repository.LoginRateLimiter;
import app.backend.jamo.identity.infrastructure.config.AuthLoginProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Redis 기반 LOCAL login 실패 제한.
 *
 * <p>email 원문을 Redis key 에 남기지 않기 위해 limiter bucket 재료를 SHA-256 으로 해시한다.
 * deviceId 는 client-controlled 이므로 단독으로 신뢰하지 않고 {@code email+clientIp} 버킷을
 * 함께 검사한다. INCR + EXPIRE 는 완전 atomic 은 아니지만 실패 제한 soft guard 로 충분하며,
 * 운영에서 강한 원자성이 필요해지면 Lua script 로 교체한다.
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiterRedisStore implements LoginRateLimiter {

    private static final String KEY_PREFIX = "auth:login:fail:";

    private final StringRedisTemplate redis;
    private final AuthLoginProperties properties;

    @Override
    public boolean isAllowed(Email email, String clientIp, String deviceId) {
        return failures(ipBucket(email, clientIp)) < properties.maxFailures()
                && failures(deviceBucket(email, deviceId)) < properties.maxFailures();
    }

    @Override
    public void recordFailure(Email email, String clientIp, String deviceId) {
        increment(ipBucket(email, clientIp));
        increment(deviceBucket(email, deviceId));
    }

    @Override
    public void reset(Email email, String clientIp, String deviceId) {
        redis.delete(ipBucket(email, clientIp));
        redis.delete(deviceBucket(email, deviceId));
    }

    private int failures(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? 0 : Integer.parseInt(value);
    }

    private void increment(String key) {
        Long failures = redis.opsForValue().increment(key);
        if (failures != null && failures == 1L) {
            redis.expire(key, properties.window());
        }
    }

    private static String ipBucket(Email email, String clientIp) {
        return KEY_PREFIX + "ip:" + sha256(email.value() + "|" + clientIp);
    }

    private static String deviceBucket(Email email, String deviceId) {
        return KEY_PREFIX + "device:" + sha256(email.value() + "|" + deviceId);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
