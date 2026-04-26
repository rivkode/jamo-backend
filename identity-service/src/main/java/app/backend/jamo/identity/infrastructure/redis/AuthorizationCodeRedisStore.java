package app.backend.jamo.identity.infrastructure.redis;

import app.backend.jamo.identity.domain.model.auth.AuthorizationCode;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.AuthorizationCodeStore;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
public class AuthorizationCodeRedisStore implements AuthorizationCodeStore {

    private static final String KEY_PREFIX = "authcode:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public AuthorizationCodeRedisStore(StringRedisTemplate redis,
                                       ObjectMapper objectMapper,
                                       OAuthProviderProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = properties.authcodeTtl();
    }

    @Override
    public void store(AuthorizationCode code) {
        Payload payload = Payload.from(code);
        try {
            String json = objectMapper.writeValueAsString(payload);
            redis.opsForValue().set(key(code.value()), json, ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize authorization code payload", e);
        }
    }

    @Override
    public Optional<AuthorizationCode> consume(String codeValue) {
        String json = redis.opsForValue().getAndDelete(key(codeValue));
        if (json == null) {
            return Optional.empty();
        }
        try {
            Payload payload = objectMapper.readValue(json, Payload.class);
            return Optional.of(payload.toCode(codeValue));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to deserialize authorization code payload", e);
        }
    }

    private String key(String codeValue) {
        return KEY_PREFIX + codeValue;
    }

    private record Payload(
            String userId,
            String sessionId,
            String deviceId,
            Instant issuedAt,
            Instant expiresAt
    ) {
        static Payload from(AuthorizationCode code) {
            return new Payload(
                    code.userId().asString(),
                    code.sessionId(),
                    code.deviceId(),
                    code.issuedAt(),
                    code.expiresAt()
            );
        }

        AuthorizationCode toCode(String value) {
            return new AuthorizationCode(
                    value,
                    UserId.fromString(userId),
                    sessionId,
                    deviceId,
                    issuedAt,
                    expiresAt
            );
        }
    }
}
