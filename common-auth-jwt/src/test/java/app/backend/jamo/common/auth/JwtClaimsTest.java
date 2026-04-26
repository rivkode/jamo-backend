package app.backend.jamo.common.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtClaimsTest {

    private static final Instant ISSUED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EXPIRES = ISSUED.plusSeconds(900);

    @Test
    void valid_claims_are_constructed() {
        JwtClaims claims = new JwtClaims("user-1", "sid-1", "device-1", JwtTokenType.ACCESS, ISSUED, EXPIRES);

        assertThat(claims.subject()).isEqualTo("user-1");
        assertThat(claims.tokenType()).isEqualTo(JwtTokenType.ACCESS);
    }

    @Test
    void blank_subject_is_rejected() {
        assertThatThrownBy(() -> new JwtClaims(" ", "sid", "device", JwtTokenType.ACCESS, ISSUED, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject");
    }

    @Test
    void null_session_id_is_rejected() {
        assertThatThrownBy(() -> new JwtClaims("user", null, "device", JwtTokenType.ACCESS, ISSUED, EXPIRES))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sessionId");
    }

    @Test
    void blank_device_id_is_rejected() {
        assertThatThrownBy(() -> new JwtClaims("user", "sid", "", JwtTokenType.ACCESS, ISSUED, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deviceId");
    }

    @Test
    void expires_at_must_be_after_issued_at() {
        assertThatThrownBy(() -> new JwtClaims("user", "sid", "device", JwtTokenType.ACCESS, EXPIRES, ISSUED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt");
    }
}
