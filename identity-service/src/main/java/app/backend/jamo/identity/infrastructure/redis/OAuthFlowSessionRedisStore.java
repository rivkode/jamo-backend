package app.backend.jamo.identity.infrastructure.redis;

import app.backend.jamo.identity.domain.model.auth.AuthState;
import app.backend.jamo.identity.domain.model.auth.OAuthFlowSession;
import app.backend.jamo.identity.domain.model.auth.PkceVerifier;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.repository.OAuthFlowSessionStore;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Redis 기반 OAuth flowSession 보관소.
 * TTL = state-cookie max-age (둘이 동기화 — cookie 가 살아 있으면 Redis 에도 살아 있어야).
 * consume 은 GETDEL atomic — state replay 차단.
 */
@Component
public class OAuthFlowSessionRedisStore implements OAuthFlowSessionStore {

    private static final String KEY_PREFIX = "oauth_flow:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public OAuthFlowSessionRedisStore(StringRedisTemplate redis,
                                      ObjectMapper objectMapper,
                                      OAuthProviderProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = properties.stateCookie().maxAge();
    }

    @Override
    public void store(OAuthFlowSession session) {
        try {
            String json = objectMapper.writeValueAsString(Payload.from(session));
            redis.opsForValue().set(key(session.state()), json, ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize OAuth flow session", e);
        }
    }

    @Override
    public Optional<OAuthFlowSession> consume(AuthState state) {
        String json = redis.opsForValue().getAndDelete(key(state));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, Payload.class).toDomain());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to deserialize OAuth flow session", e);
        }
    }

    private String key(AuthState state) {
        return KEY_PREFIX + state.value();
    }

    /**
     * Redis 직렬화 전용 — 도메인 VO 의 nested 구조를 평탄화.
     */
    private record Payload(
            String state,
            String provider,
            String pkceVerifier,
            String deviceId,
            String redirectUri,
            Instant issuedAt,
            Instant expiresAt
    ) {
        static Payload from(OAuthFlowSession s) {
            return new Payload(
                    s.state().value(),
                    s.provider().name(),
                    s.pkceVerifierOpt().map(PkceVerifier::value).orElse(null),
                    s.deviceId(),
                    s.redirectUri(),
                    s.issuedAt(),
                    s.expiresAt()
            );
        }

        OAuthFlowSession toDomain() {
            return new OAuthFlowSession(
                    new AuthState(state),
                    OAuthProvider.valueOf(provider),
                    pkceVerifier != null ? new PkceVerifier(pkceVerifier) : null,
                    deviceId,
                    redirectUri,
                    issuedAt,
                    expiresAt
            );
        }
    }
}
