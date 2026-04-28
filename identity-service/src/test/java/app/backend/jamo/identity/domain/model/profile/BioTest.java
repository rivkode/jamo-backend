package app.backend.jamo.identity.domain.model.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BioTest {

    @Test
    void valid_bio_is_accepted() {
        assertThat(new Bio("Hello, jamo!").value()).isEqualTo("Hello, jamo!");
    }

    @Test
    void max_length_bio_is_accepted() {
        assertThat(new Bio("a".repeat(Bio.MAX_LENGTH)).value())
                .hasSize(Bio.MAX_LENGTH);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", " ", "   ", "\t", "\n" })
    void null_or_blank_is_rejected(String input) {
        assertThatThrownBy(() -> new Bio(input))
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class);
    }

    @Test
    void overly_long_bio_is_rejected() {
        String tooLong = "a".repeat(Bio.MAX_LENGTH + 1);
        assertThatThrownBy(() -> new Bio(tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("length");
    }

    @Test
    void surrounding_whitespace_is_trimmed_into_canonical_form() {
        // canonical form 정책 — 양 끝 공백 제거 후 저장. trim 정책은 length / blank 판정에도 동일 기준 적용.
        Bio bio = new Bio("  hello  ");

        assertThat(bio.value()).isEqualTo("hello");
    }

    @Test
    void max_length_after_trim_is_accepted() {
        // 양 끝 공백을 포함해도 trim 후 길이가 MAX_LENGTH 면 통과 (canonical form)
        String value = "  " + "a".repeat(Bio.MAX_LENGTH) + "  ";

        Bio bio = new Bio(value);

        assertThat(bio.value()).hasSize(Bio.MAX_LENGTH);
    }

    @Test
    void length_exceeded_after_trim_is_rejected() {
        // trim 후에도 MAX_LENGTH 초과면 거부 — length 검증이 trim 기준임을 증명
        String value = "  " + "a".repeat(Bio.MAX_LENGTH + 1) + "  ";

        assertThatThrownBy(() -> new Bio(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("length");
    }
}
