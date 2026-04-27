package app.backend.jamo.identity.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenHashPropertiesTest {

    @Test
    void accepts_pepper_at_minimum_length() {
        RefreshTokenHashProperties p = new RefreshTokenHashProperties(
                "a".repeat(RefreshTokenHashProperties.MIN_PEPPER_LENGTH));

        assertThat(p.pepper()).hasSize(RefreshTokenHashProperties.MIN_PEPPER_LENGTH);
    }

    @Test
    void rejects_blank_pepper() {
        assertThatThrownBy(() -> new RefreshTokenHashProperties(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejects_short_pepper() {
        assertThatThrownBy(() -> new RefreshTokenHashProperties("short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    void rejects_null_pepper() {
        assertThatThrownBy(() -> new RefreshTokenHashProperties(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("pepper");
    }
}
