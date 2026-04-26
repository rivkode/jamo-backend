package app.backend.jamo.identity.infrastructure.redis;

import app.backend.jamo.identity.domain.model.auth.RefreshTokenRecord;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.RefreshTokenStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

@Component
public class RefreshTokenRedisStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RefreshTokenRedisStore(StringRedisTemplate redis,
                                  ObjectMapper objectMapper,
                                  Clock clock) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void store(RefreshTokenRecord record) {
        Duration ttl = Duration.between(clock.instant(), record.expiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("refresh token already expired");
        }
        try {
            String json = objectMapper.writeValueAsString(record);
            redis.opsForValue().set(key(record.userId(), record.sessionId()), json, ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize refresh token", e);
        }
    }

    @Override
    public Optional<RefreshTokenRecord> find(UserId userId, String sessionId) {
        String json = redis.opsForValue().get(key(userId, sessionId));
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
        redis.delete(key(userId, sessionId));
    }

    private String key(UserId userId, String sessionId) {
        return KEY_PREFIX + userId.asString() + ":" + sessionId;
    }
}
