package app.backend.jamo.identity.domain.model.auth;

import app.backend.jamo.identity.domain.model.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationCodeTest {

    private static final Instant NOW = Instant.parse("2026-04-27T10:00:00Z");
    private static final UserId USER = UserId.generate();

    @Test
    void valid_code_is_constructed() {
        AuthorizationCode code = sample();

        assertThat(code.value()).isEqualTo("abc");
        assertThat(code.userId()).isEqualTo(USER);
    }

    @Test
    void expires_at_must_be_after_issued_at() {
        assertThatThrownBy(() ->
                new AuthorizationCode("abc", USER, "sid", "device", NOW, NOW)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blank_value_is_rejected() {
        assertThatThrownBy(() ->
                new AuthorizationCode("", USER, "sid", "device", NOW, NOW.plusSeconds(60))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void is_expired_returns_true_at_expiry_instant() {
        AuthorizationCode code = sample();

        assertThat(code.isExpired(NOW.plusSeconds(60))).isTrue();
        assertThat(code.isExpired(NOW.plusSeconds(59))).isFalse();
    }

    private AuthorizationCode sample() {
        return new AuthorizationCode("abc", USER, "sid", "device", NOW, NOW.plusSeconds(60));
    }
}
