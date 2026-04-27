package app.backend.jamo.identity.infrastructure.redis;

import app.backend.jamo.identity.domain.model.auth.AuthState;
import app.backend.jamo.identity.domain.model.auth.OAuthFlowSession;
import app.backend.jamo.identity.domain.model.auth.PkceVerifier;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties.ProviderConfig;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties.StateCookieConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthFlowSessionRedisStoreTest {

    private static final Instant NOW = Instant.parse("2026-04-27T10:00:00Z");

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private OAuthFlowSessionRedisStore store;
    private ObjectMapper objectMapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        OAuthProviderProperties props = new OAuthProviderProperties(
                Duration.ofSeconds(60),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Map.of("kakao", new ProviderConfig(
                        "c", "s", "http://test/cb",
                        "https://p/auth", "https://p/token", "https://p/userinfo",
                        "scope", false)),
                new StateCookieConfig(null, false, "Lax", Duration.ofMinutes(5))
        );
        store = new OAuthFlowSessionRedisStore(redis, objectMapper, props);
    }

    private OAuthFlowSession sample(PkceVerifier verifier) {
        return new OAuthFlowSession(
                AuthState.random(),
                OAuthProvider.GOOGLE,
                verifier,
                "device-1",
                "http://test/cb/google",
                NOW,
                NOW.plus(Duration.ofMinutes(5))
        );
    }

    @Test
    void store_writes_serialized_payload_with_ttl() {
        OAuthFlowSession session = sample(PkceVerifier.random(new SecureRandom()));

        store.store(session);

        verify(ops).set(eq("oauth_flow:" + session.state().value()),
                any(String.class), eq(Duration.ofMinutes(5)));
    }

    @Test
    void consume_returns_session_when_present_with_pkce_verifier() throws Exception {
        OAuthFlowSession original = sample(PkceVerifier.random(new SecureRandom()));
        String key = "oauth_flow:" + original.state().value();

        // store flow 와 동일 직렬화 — 라운드트립 검증을 위해 실제 ObjectMapper 통한 JSON 사용
        store.store(original);
        // verify what was passed to set then return it from getAndDelete
        org.mockito.ArgumentCaptor<String> jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ops).set(eq(key), jsonCaptor.capture(), any(Duration.class));
        when(ops.getAndDelete(key)).thenReturn(jsonCaptor.getValue());

        Optional<OAuthFlowSession> consumed = store.consume(original.state());

        assertThat(consumed).isPresent();
        OAuthFlowSession s = consumed.get();
        assertThat(s.state()).isEqualTo(original.state());
        assertThat(s.provider()).isEqualTo(original.provider());
        assertThat(s.deviceId()).isEqualTo(original.deviceId());
        assertThat(s.redirectUri()).isEqualTo(original.redirectUri());
        assertThat(s.issuedAt()).isEqualTo(original.issuedAt());
        assertThat(s.expiresAt()).isEqualTo(original.expiresAt());
        assertThat(s.pkceVerifierOpt()).isEqualTo(original.pkceVerifierOpt());
    }

    @Test
    void consume_returns_session_when_present_without_pkce_verifier() {
        OAuthFlowSession original = sample(null);
        store.store(original);
        org.mockito.ArgumentCaptor<String> jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ops).set(any(), jsonCaptor.capture(), any(Duration.class));
        when(ops.getAndDelete("oauth_flow:" + original.state().value())).thenReturn(jsonCaptor.getValue());

        Optional<OAuthFlowSession> consumed = store.consume(original.state());

        assertThat(consumed).isPresent();
        assertThat(consumed.get().pkceVerifierOpt()).isEmpty();
    }

    @Test
    void consume_returns_empty_when_key_missing() {
        AuthState state = AuthState.random();
        when(ops.getAndDelete("oauth_flow:" + state.value())).thenReturn(null);

        Optional<OAuthFlowSession> result = store.consume(state);

        assertThat(result).isEmpty();
    }
}
