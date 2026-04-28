package app.backend.jamo.identity.infrastructure.redis;

import app.backend.jamo.identity.domain.exception.DisplayNameChangeTooFrequentException;
import app.backend.jamo.identity.domain.model.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisplayNameChangeRateLimiterRedisStoreTest {

    private static final UserId USER_ID = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final String EXPECTED_KEY = "user:displayName_changed:" + USER_ID.value();

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private DisplayNameChangeRateLimiterRedisStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        store = new DisplayNameChangeRateLimiterRedisStore(redis);
    }

    @Test
    void check_passes_when_key_missing() {
        when(redis.hasKey(EXPECTED_KEY)).thenReturn(Boolean.FALSE);

        assertThatCode(() -> store.check(USER_ID)).doesNotThrowAnyException();
    }

    @Test
    void check_passes_when_redis_returns_null() {
        when(redis.hasKey(EXPECTED_KEY)).thenReturn(null);

        assertThatCode(() -> store.check(USER_ID)).doesNotThrowAnyException();
    }

    @Test
    void check_throws_when_key_exists() {
        when(redis.hasKey(EXPECTED_KEY)).thenReturn(Boolean.TRUE);

        assertThatThrownBy(() -> store.check(USER_ID))
                .isInstanceOf(DisplayNameChangeTooFrequentException.class);
    }

    @Test
    void markChanged_writes_value_with_ttl_at_user_key() {
        store.markChanged(USER_ID, Duration.ofDays(7));

        verify(ops).set(eq(EXPECTED_KEY), eq("1"), eq(Duration.ofDays(7)));
    }

    @Test
    void markChanged_rejects_zero_or_negative_ttl() {
        assertThatThrownBy(() -> store.markChanged(USER_ID, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.markChanged(USER_ID, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markChanged_rejects_null_ttl() {
        assertThatThrownBy(() -> store.markChanged(USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
