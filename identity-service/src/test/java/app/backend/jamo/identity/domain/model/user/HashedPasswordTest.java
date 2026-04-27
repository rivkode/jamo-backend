package app.backend.jamo.identity.domain.model.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashedPasswordTest {

    @Test
    void wraps_non_blank_value() {
        HashedPassword hashed = new HashedPassword("$2a$12$abc");

        assertThat(hashed.value()).isEqualTo("$2a$12$abc");
    }

    @Test
    void rejects_null() {
        assertThatThrownBy(() -> new HashedPassword(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_blank() {
        assertThatThrownBy(() -> new HashedPassword("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void to_string_masks_value_to_avoid_log_leak() {
        HashedPassword hashed = new HashedPassword("$2a$12$secret-hash");

        assertThat(hashed.toString()).doesNotContain("$2a$12$secret-hash");
        assertThat(hashed.toString()).isEqualTo("HashedPassword[***]");
    }

    @Test
    void same_value_implies_equality_and_hash() {
        assertThat(new HashedPassword("$2a$12$x"))
                .isEqualTo(new HashedPassword("$2a$12$x"))
                .hasSameHashCodeAs(new HashedPassword("$2a$12$x"));
    }
}
