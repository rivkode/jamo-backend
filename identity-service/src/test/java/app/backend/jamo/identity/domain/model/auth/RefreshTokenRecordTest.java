package app.backend.jamo.identity.domain.model.auth;

import app.backend.jamo.identity.domain.model.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenRecordTest {

    private static final Instant NOW = Instant.parse("2026-04-27T10:00:00Z");
    private static final UserId USER = UserId.generate();

    @Test
    void blank_session_id_is_rejected() {
        assertThatThrownBy(() ->
                new RefreshTokenRecord(USER, "", "device", "hash", NOW, NOW.plusSeconds(60))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blank_token_hash_is_rejected() {
        assertThatThrownBy(() ->
                new RefreshTokenRecord(USER, "sid", "device", "", NOW, NOW.plusSeconds(60))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expires_at_must_be_after_issued_at() {
        assertThatThrownBy(() ->
                new RefreshTokenRecord(USER, "sid", "device", "hash", NOW, NOW)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
