package app.backend.jamo.identity.infrastructure.security;

import app.backend.jamo.identity.infrastructure.config.RefreshTokenHashProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacRefreshTokenHasherTest {

    private static final String PEPPER = "a".repeat(64);
    private static final HmacRefreshTokenHasher HASHER =
            new HmacRefreshTokenHasher(new RefreshTokenHashProperties(PEPPER));

    @Test
    void hash_is_deterministic_for_same_input() {
        String token = "header.payload.signature";

        String first = HASHER.hash(token);
        String second = HASHER.hash(token);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void hash_changes_when_input_differs() {
        assertThat(HASHER.hash("token-a")).isNotEqualTo(HASHER.hash("token-b"));
    }

    @Test
    void hash_changes_when_pepper_differs() {
        HmacRefreshTokenHasher other = new HmacRefreshTokenHasher(
                new RefreshTokenHashProperties("b".repeat(64)));

        assertThat(HASHER.hash("token-x")).isNotEqualTo(other.hash("token-x"));
    }

    @Test
    void hash_output_is_base64url_no_padding() {
        String hash = HASHER.hash("any.jwt.token");

        assertThat(hash).matches("^[A-Za-z0-9_-]+$");  // base64url charset
        assertThat(hash).doesNotContain("=");
    }

    @Test
    void rejects_blank_token() {
        assertThatThrownBy(() -> HASHER.hash(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejects_null_token() {
        assertThatThrownBy(() -> HASHER.hash(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("refreshTokenJwt");
    }
}
