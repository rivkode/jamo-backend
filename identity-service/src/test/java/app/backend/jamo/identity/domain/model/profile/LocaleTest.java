package app.backend.jamo.identity.domain.model.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocaleTest {

    @ParameterizedTest
    @ValueSource(strings = { "ko", "en" })
    void allowed_locale_is_accepted(String code) {
        assertThat(new Locale(code).code()).isEqualTo(code);
    }

    @ParameterizedTest
    @NullSource
    void null_is_rejected(String input) {
        assertThatThrownBy(() -> new Locale(input))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "KO", "Ko", "ja", "fr", "ko-KR", "en_US", "kor" })
    void unsupported_locale_is_rejected(String code) {
        assertThatThrownBy(() -> new Locale(code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported locale");
    }

    @Test
    void default_locale_is_ko() {
        assertThat(Locale.DEFAULT.code()).isEqualTo("ko");
    }

    @Test
    void allowed_set_includes_ko_and_en_only() {
        assertThat(Locale.allowed()).containsExactlyInAnyOrder("ko", "en");
    }
}
