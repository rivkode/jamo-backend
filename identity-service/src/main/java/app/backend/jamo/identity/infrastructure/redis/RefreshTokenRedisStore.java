package app.backend.jamo.identity.infrastructure.redis;

import app.backend.jamo.identity.domain.model.auth.RefreshTokenRecord;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.RefreshTokenStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Refresh JWT hash 의 Redis 보관 — 두 키 그룹으로 구성.
 *
 * <ul>
 *   <li>{@code refresh:{userId}:{sessionId}} — record JSON. TTL=refresh JWT 만료까지.</li>
 *   <li>{@code refresh:user:{userId}} — sessionId 보조 인덱스 (Set). reuse detection 시
 *       user 의 모든 sid 일괄 수집에 사용. SET TTL 도 record 와 동일 TTL 로 EXPIRE 갱신해
 *       inactive user 메모리 누적을 방지.</li>
 * </ul>
 *
 * <p>두 키는 atomic 하지 않다 — Redis Lua/MULTI 미사용. 부분 실패 영향:
 * <ul>
 *   <li><b>store 의 SADD 실패</b> — record 는 살아있으나 인덱스 누락 →
 *       {@link #findAllSessionIds} 가 해당 sid 를 못 봄. reuse detection 시 사각지대 가능
 *       (다음 정상 store 에서 인덱스 회복).</li>
 *   <li><b>delete 의 SREM 실패</b> — record 는 사라졌으나 인덱스에 좀비 sid →
 *       {@link #findAllSessionIds} 가 폐기된 sid 반환. reuse compensation 의 후속 delete 는
 *       no-op 이라 안전. record TTL 까지 SET TTL 갱신이 일어나지 않으면 인덱스 SET 도 자연 만료.</li>
 * </ul>
 * 영향 비대칭은 best-effort 보상 트랜잭션 정신과 정합 (decisions/auth/refresh-rotation-blacklist-ports.md
 * D6). 더 강한 정합이 필요해지면 Lua script 로 atomic 화 고려.
 *
 * <p>키 충돌: userId 가 UUID v4 (8-4-4-4-12 hex) 형식이라 첫 세그먼트가 절대 {@code "user"}
 * 가 될 수 없어 {@link #KEY_PREFIX} 와 {@link #USER_INDEX_PREFIX} 의 어휘적 분리 보장.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenRedisStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";
    private static final String USER_INDEX_PREFIX = "refresh:user:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public void store(RefreshTokenRecord record) {
        Duration ttl = Duration.between(clock.instant(), record.expiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("refresh token already expired");
        }
        try {
            String json = objectMapper.writeValueAsString(record);
            redis.opsForValue().set(recordKey(record.userId(), record.sessionId()), json, ttl);
            String indexKey = userIndexKey(record.userId());
            redis.opsForSet().add(indexKey, record.sessionId());
            redis.expire(indexKey, ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize refresh token", e);
        }
    }

    @Override
    public Optional<RefreshTokenRecord> find(UserId userId, String sessionId) {
        String json = redis.opsForValue().get(recordKey(userId, sessionId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, RefreshTokenRecord.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to deserialize refresh token", e);
        }
    }

    @Override
    public void delete(UserId userId, String sessionId) {
        redis.delete(recordKey(userId, sessionId));
        SetOperations<String, String> setOps = redis.opsForSet();
        setOps.remove(userIndexKey(userId), sessionId);
    }

    @Override
    public Set<String> findAllSessionIds(UserId userId) {
        Set<String> members = redis.opsForSet().members(userIndexKey(userId));
        return members == null ? Collections.emptySet() : members;
    }

    private String recordKey(UserId userId, String sessionId) {
        return KEY_PREFIX + userId.asString() + ":" + sessionId;
    }

    private String userIndexKey(UserId userId) {
        return USER_INDEX_PREFIX + userId.asString();
    }
}
