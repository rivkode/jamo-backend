package app.backend.jamo.identity.domain.model.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationCodeTest {

    @Test
    @DisplayName("정확히 6자리 숫자는 ValidationCode 생성")
    void valid_six_digit_code() {
        ValidationCode code = ValidationCode.of("123456");

        assertThat(code.value()).isEqualTo("123456");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1234567", "0", ""})
    @DisplayName("길이가 6 이 아니면 IllegalArgumentException")
    void rejects_when_length_not_six(String invalid) {
        assertThatThrownBy(() -> ValidationCode.of(invalid))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdef", "12345a", " 12345", "123456 ", "  ", "12 456"})
    @DisplayName("숫자 외 문자/공백 포함 시 IllegalArgumentException")
    void rejects_when_non_digit(String invalid) {
        assertThatThrownBy(() -> ValidationCode.of(invalid))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 입력은 NullPointerException")
    void null_throws_npe() {
        assertThatThrownBy(() -> ValidationCode.of(null))
            .isInstanceOf(NullPointerException.class);
    }

    @RepeatedTest(value = 50, name = "generate {currentRepetition}/{totalRepetitions} — 6자리 숫자")
    @DisplayName("generate 는 정확히 6자리 숫자 코드 반환 (0 패딩 포함)")
    void generate_produces_six_digit_numeric() {
        ValidationCode code = ValidationCode.generate();

        assertThat(code.value()).matches("^\\d{6}$");
    }
}
