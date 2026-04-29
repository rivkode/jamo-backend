package app.backend.jamo.identity.infrastructure.redis;

import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.ValidationCode;
import app.backend.jamo.identity.domain.repository.ValidationCodeStore;
import app.backend.jamo.identity.infrastructure.config.EmailValidationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * {@link ValidationCodeStore} 의 Redis 구현 — 두 키 그룹.
 *
 * <ul>
 *   <li>{@code user:validation:code:{email}} — 6자리 코드 raw string. TTL = {@code codeTtl} (5분).</li>
 *   <li>{@code user:validation:attempts:{email}} — INCR 카운터. TTL 은 첫 increment 시점부터
 *       {@code codeTtl} 로 동기화 (코드 만료 시 attempts 도 자연 정리).</li>
 * </ul>
 *
 * <p>두 키는 atomic 하지 않다 — Redis Lua/MULTI 미사용. 부분 실패 영향:
 * <ul>
 *   <li><b>{@link #issue} 의 attempts DELETE 실패</b> — 새 코드 + 이전 attempts 카운터 잔존.
 *       사용자가 첫 시도 전에 잠금 가능 (worst-case). 다음 issue 시 회복. 영향 윈도우 = codeTtl.</li>
 *   <li><b>{@link #incrementAttempts} 의 EXPIRE 실패</b> — 카운터에 TTL 미설정. 다음 issue 의
 *       명시적 delete 로 회복. 발송 없이 verify 만 반복 시 카운터 누적되지만 코드 부재로
 *       즉시 ValidationCodeExpiredException → 실질 영향 없음.</li>
 * </ul>
 * 더 강한 정합 필요 시 Lua script 도입 검토.
 */
@Component
@RequiredArgsConstructor
public class ValidationCodeRedisStore implements ValidationCodeStore {

    private static final String CODE_KEY_PREFIX = "user:validation:code:";
    private static final String ATTEMPTS_KEY_PREFIX = "user:validation:attempts:";

    private final StringRedisTemplate redis;
    private final EmailValidationProperties properties;

    @Override
    public void issue(Email email, ValidationCode code, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        redis.opsForValue().set(codeKey(email), code.value(), ttl);
        redis.delete(attemptsKey(email));
    }

    @Override
    public Optional<ValidationCode> find(Email email) {
        String value = redis.opsForValue().get(codeKey(email));
        return value == null ? Optional.empty() : Optional.of(ValidationCode.of(value));
    }

    @Override
    public int incrementAttempts(Email email) {
        String key = attemptsKey(email);
        Long count = redis.opsForValue().increment(key);
        redis.expire(key, properties.codeTtl());
        return count == null ? 1 : count.intValue();
    }

    @Override
    public void invalidate(Email email) {
        redis.delete(codeKey(email));
        redis.delete(attemptsKey(email));
    }

    private String codeKey(Email email) {
        return CODE_KEY_PREFIX + email.value();
    }

    private String attemptsKey(Email email) {
        return ATTEMPTS_KEY_PREFIX + email.value();
    }
}
