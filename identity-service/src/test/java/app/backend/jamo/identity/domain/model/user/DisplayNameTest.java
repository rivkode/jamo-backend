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

    @Test
    void truncated_returns_unchanged_when_within_limit() {
        DisplayNameTruncation result = DisplayName.truncated("jamo");

        assertThat(result.displayName().value()).isEqualTo("jamo");
        assertThat(result.wasTruncated()).isFalse();
    }

    @Test
    void truncated_returns_thirty_two_chars_when_exceeds() {
        String raw = "a".repeat(50);
        DisplayNameTruncation result = DisplayName.truncated(raw);

        assertThat(result.displayName().value()).hasSize(32);
        assertThat(result.wasTruncated()).isTrue();
    }

    @Test
    void truncated_handles_exactly_thirty_two_chars() {
        String raw = "a".repeat(32);
        DisplayNameTruncation result = DisplayName.truncated(raw);

        assertThat(result.wasTruncated()).isFalse();
    }

    @Test
    void truncated_trims_surrounding_whitespace() {
        DisplayNameTruncation result = DisplayName.truncated("  jamo  ");

        assertThat(result.displayName().value()).isEqualTo("jamo");
        assertThat(result.wasTruncated()).isFalse();
    }

    @Test
    void truncated_rejects_blank_after_trim() {
        assertThatThrownBy(() -> DisplayName.truncated("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void truncated_rejects_null() {
        assertThatThrownBy(() -> DisplayName.truncated(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("raw");
    }
}
