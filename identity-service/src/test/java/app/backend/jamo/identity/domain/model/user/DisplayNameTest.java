package app.backend.jamo.identity.domain.model.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisplayNameTest {

    @Test
    void valid_name_is_accepted() {
        assertThat(new DisplayName("jamo").value()).isEqualTo("jamo");
    }

    @Test
    void blank_name_is_rejected() {
        assertThatThrownBy(() -> new DisplayName("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overly_long_name_is_rejected() {
        assertThatThrownBy(() -> new DisplayName("a".repeat(33))).isInstanceOf(IllegalArgumentException.class);
    }
}
