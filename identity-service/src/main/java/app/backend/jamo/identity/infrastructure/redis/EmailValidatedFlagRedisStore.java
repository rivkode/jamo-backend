package app.backend.jamo.identity.infrastructure.redis;

import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.repository.EmailValidatedFlag;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@link EmailValidatedFlag} 의 Redis 구현 — 단일 KEY (`user:email_validated:{email}`).
 *
 * <p>{@link #consume} 은 GETDEL atomic 으로 동시 가입 시도 중 한 쪽만 성공하도록 보장
 * (Spring Data Redis 6.2+ {@code getAndDelete}).
 *
 * <p><b>TTL 외부 주입 — 다른 두 어댑터와의 비대칭 의도</b>: {@link ValidationCodeRedisStore} /
 * {@link ValidationRateLimiterRedisStore} 가 {@code EmailValidationProperties} 를 주입받는
 * 반면, 본 어댑터는 미주입. 이유: validated flag 는 createUser 외에도 향후 password reset
 * 등 다른 use case 가 같은 flag 메커니즘을 재사용 가능하므로, TTL 결정을 호출자 application
 * service 가 갖도록 위임. {@code SessionBlacklist} 가 {@code Duration ttl} 시그니처를 쓰는
 * 것과 같은 패턴.
 */
@Component
public class EmailValidatedFlagRedisStore implements EmailValidatedFlag {

    private static final String KEY_PREFIX = "user:email_validated:";
    private static final String VALUE = "1";

    private final StringRedisTemplate redis;

    public EmailValidatedFlagRedisStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void mark(Email email, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        redis.opsForValue().set(key(email), VALUE, ttl);
    }

    @Override
    public boolean consume(Email email) {
        String value = redis.opsForValue().getAndDelete(key(email));
        return value != null;
    }

    private String key(Email email) {
        return KEY_PREFIX + email.value();
    }
}
