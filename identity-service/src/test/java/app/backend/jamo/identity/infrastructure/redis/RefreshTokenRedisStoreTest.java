package app.backend.jamo.identity.infrastructure.redis;

import app.backend.jamo.identity.domain.model.auth.RefreshTokenRecord;
import app.backend.jamo.identity.domain.model.user.UserId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenRedisStoreTest {

    private static final Instant NOW = Instant.parse("2026-04-27T10:00:00Z");
    private static final UserId USER_ID = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private SetOperations<String, String> setOps;
    private RefreshTokenRedisStore store;
    private ObjectMapper objectMapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        setOps = mock(SetOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForSet()).thenReturn(setOps);

        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        store = new RefreshTokenRedisStore(redis, objectMapper, clock);
    }

    private RefreshTokenRecord sample(String sessionId) {
        return new RefreshTokenRecord(
                USER_ID, sessionId, "device-1", "hash-" + sessionId,
                NOW, NOW.plus(Duration.ofDays(7))
        );
    }

    @Test
    void store_writes_record_with_ttl_and_adds_to_user_index_with_expire() {
        RefreshTokenRecord record = sample("sid-1");

        store.store(record);

        verify(valueOps).set(eq("refresh:" + USER_ID.asString() + ":sid-1"),
                any(String.class), eq(Duration.ofDays(7)));
        verify(setOps).add(eq("refresh:user:" + USER_ID.asString()), eq("sid-1"));
        verify(redis).expire(eq("refresh:user:" + USER_ID.asString()), eq(Duration.ofDays(7)));
    }

    @Test
    void store_rejects_record_already_expired() {
        RefreshTokenRecord expired = new RefreshTokenRecord(
                USER_ID, "sid-1", "device-1", "h", NOW.minus(Duration.ofDays(1)), NOW);

        assertThatThrownBy(() -> store.store(expired))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void find_returns_record_when_present() throws Exception {
        RefreshTokenRecord record = sample("sid-1");
        String json = objectMapper.writeValueAsString(record);
        when(valueOps.get("refresh:" + USER_ID.asString() + ":sid-1")).thenReturn(json);

        Optional<RefreshTokenRecord> found = store.find(USER_ID, "sid-1");

        assertThat(found).isPresent();
        assertThat(found.get().sessionId()).isEqualTo("sid-1");
        assertThat(found.get().tokenHash()).isEqualTo("hash-sid-1");
    }

    @Test
    void find_returns_empty_when_key_missing() {
        when(valueOps.get("refresh:" + USER_ID.asString() + ":sid-1")).thenReturn(null);

        assertThat(store.find(USER_ID, "sid-1")).isEmpty();
    }

    @Test
    void delete_removes_record_key_and_index_member() {
        store.delete(USER_ID, "sid-1");

        verify(redis).delete("refresh:" + USER_ID.asString() + ":sid-1");
        verify(setOps).remove(eq("refresh:user:" + USER_ID.asString()), eq("sid-1"));
    }

    @Test
    void findAllSessionIds_returns_index_members() {
        when(setOps.members("refresh:user:" + USER_ID.asString()))
                .thenReturn(Set.of("sid-1", "sid-2", "sid-3"));

        Set<String> sids = store.findAllSessionIds(USER_ID);

        assertThat(sids).containsExactlyInAnyOrder("sid-1", "sid-2", "sid-3");
    }

    @Test
    void findAllSessionIds_returns_empty_set_when_user_has_no_active_sids() {
        when(setOps.members("refresh:user:" + USER_ID.asString())).thenReturn(null);

        assertThat(store.findAllSessionIds(USER_ID)).isEmpty();
    }
}
