package app.backend.jamo.identity.infrastructure.external;

import app.backend.jamo.identity.domain.model.user.HashedPassword;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BCryptPasswordEncoderAdapterTest {

    private final BCryptPasswordEncoderAdapter adapter = new BCryptPasswordEncoderAdapter();

    @Test
    void encode_produces_bcrypt_hash_with_cost_12() {
        HashedPassword hashed = adapter.encode("PlainPa$$w0rd");

        assertThat(hashed.value()).startsWith("$2a$12$");
        // BCrypt 출력은 정확히 60자 (prefix 7 + salt 22 + hash 31)
        assertThat(hashed.value()).hasSize(60);
    }

    @Test
    void encode_two_calls_produce_different_hashes_due_to_random_salt() {
        HashedPassword first = adapter.encode("samePassword");
        HashedPassword second = adapter.encode("samePassword");

        assertThat(first.value()).isNotEqualTo(second.value());
    }

    @Test
    void matches_returns_true_for_correct_password() {
        HashedPassword hashed = adapter.encode("PlainPa$$w0rd");

        assertThat(adapter.matches("PlainPa$$w0rd", hashed)).isTrue();
    }

    @Test
    void matches_returns_false_for_wrong_password() {
        HashedPassword hashed = adapter.encode("PlainPa$$w0rd");

        assertThat(adapter.matches("WrongPassword", hashed)).isFalse();
    }

    @Test
    void encode_rejects_null_or_empty() {
        assertThatThrownBy(() -> adapter.encode(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> adapter.encode(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matches_rejects_null_args() {
        HashedPassword hashed = adapter.encode("x");

        assertThatThrownBy(() -> adapter.matches(null, hashed))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> adapter.matches("x", null))
                .isInstanceOf(NullPointerException.class);
    }
}
