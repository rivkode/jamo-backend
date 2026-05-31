package app.backend.jamo.diary.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsPropertiesTest {

    @Test
    void single_https_origin_accepted() {
        CorsProperties props = new CorsProperties(List.of("https://jamoai.app"));
        assertThat(props.allowedOrigins()).containsExactly("https://jamoai.app");
    }

    @Test
    void multiple_origins_accepted_and_copied_immutable() {
        var mutable = new java.util.ArrayList<>(List.of("http://localhost:3000", "https://jamoai.app"));
        CorsProperties props = new CorsProperties(mutable);
        mutable.clear();
        assertThat(props.allowedOrigins()).containsExactly("http://localhost:3000", "https://jamoai.app");
    }

    @Test
    void null_list_rejected() {
        assertThatThrownBy(() -> new CorsProperties(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void empty_list_rejected() {
        assertThatThrownBy(() -> new CorsProperties(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void blank_entry_rejected() {
        assertThatThrownBy(() -> new CorsProperties(List.of("http://localhost:3000", "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void null_entry_rejected() {
        assertThatThrownBy(() -> new CorsProperties(Arrays.asList("http://localhost:3000", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void origin_without_scheme_rejected() {
        assertThatThrownBy(() -> new CorsProperties(List.of("localhost:3000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http:// or https://");
    }

    @Test
    void origin_with_trailing_slash_rejected() {
        assertThatThrownBy(() -> new CorsProperties(List.of("http://localhost:3000/")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing slash");
    }

    @Test
    void http_origin_allowed_for_local_dev() {
        assertThatCode(() -> new CorsProperties(List.of("http://localhost:3000")))
                .doesNotThrowAnyException();
    }
}
