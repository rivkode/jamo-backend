package app.backend.jamo.identity.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionBlacklistRedisStoreTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private SessionBlacklistRedisStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        store = new SessionBlacklistRedisStore(redis);
    }

    @Test
    void blacklist_writes_value_with_ttl_at_sid_key() {
        store.blacklist("sid-abc", Duration.ofMinutes(15));

        verify(ops).set(eq("bl:sid:sid-abc"), eq("1"), eq(Duration.ofMinutes(15)));
    }

    @Test
    void blacklist_rejects_blank_sessionId() {
        assertThatThrownBy(() -> store.blacklist("  ", Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(ops, never()).set(eq("bl:sid:  "), eq("1"), eq(Duration.ofMinutes(15)));
    }

    @Test
    void blacklist_rejects_non_positive_ttl() {
        assertThatThrownBy(() -> store.blacklist("sid", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.blacklist("sid", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contains_returns_true_when_key_exists() {
        when(redis.hasKey("bl:sid:sid-abc")).thenReturn(Boolean.TRUE);

        assertThat(store.contains("sid-abc")).isTrue();
    }

    @Test
    void contains_returns_false_when_key_missing() {
        when(redis.hasKey("bl:sid:sid-abc")).thenReturn(Boolean.FALSE);

        assertThat(store.contains("sid-abc")).isFalse();
    }

    @Test
    void contains_returns_false_when_redis_returns_null() {
        when(redis.hasKey("bl:sid:sid-abc")).thenReturn(null);

        assertThat(store.contains("sid-abc")).isFalse();
    }

    @Test
    void contains_returns_false_for_blank_input_without_redis_call() {
        assertThat(store.contains(null)).isFalse();
        assertThat(store.contains("  ")).isFalse();
    }
}
