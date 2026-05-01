package app.backend.jamo.identity.infrastructure.redis;

import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.infrastructure.config.AuthLoginProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginRateLimiterRedisStoreTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private LoginRateLimiterRedisStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        store = new LoginRateLimiterRedisStore(redis, new AuthLoginProperties(5, Duration.ofMinutes(15)));
    }

    @Test
    void isAllowed_when_ip_bucket_exceeded_rejects_even_if_device_rotates() {
        when(values.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return key.contains(":ip:") ? "5" : null;
        });

        boolean allowed = store.isAllowed(new Email("user@example.com"), "127.0.0.1", "rotated-device");

        assertThat(allowed).isFalse();
    }

    @Test
    void isAllowed_when_device_bucket_exceeded_rejects_even_if_ip_bucket_is_clear() {
        when(values.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return key.contains(":device:") ? "5" : null;
        });

        boolean allowed = store.isAllowed(new Email("user@example.com"), "127.0.0.1", "device-1234");

        assertThat(allowed).isFalse();
    }

    @Test
    void recordFailure_increments_ip_and_device_buckets() {
        when(values.increment(anyString())).thenReturn(1L);

        store.recordFailure(new Email("user@example.com"), "127.0.0.1", "device-1234");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(values, times(2)).increment(keyCaptor.capture());
        assertThat(keyCaptor.getAllValues()).anyMatch(key -> key.contains(":ip:"));
        assertThat(keyCaptor.getAllValues()).anyMatch(key -> key.contains(":device:"));
        assertThat(keyCaptor.getAllValues()).allSatisfy(key -> assertThat(key).doesNotContain("user@example.com"));
        verify(redis, times(2)).expire(anyString(), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(15)));
    }

    @Test
    void reset_deletes_ip_and_device_buckets() {
        store.reset(new Email("user@example.com"), "127.0.0.1", "device-1234");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redis, times(2)).delete(keyCaptor.capture());
        assertThat(keyCaptor.getAllValues()).anyMatch(key -> key.contains(":ip:"));
        assertThat(keyCaptor.getAllValues()).anyMatch(key -> key.contains(":device:"));
    }
}
