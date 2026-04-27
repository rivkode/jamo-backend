package app.backend.jamo.identity.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrontendPropertiesTest {

    @Test
    void accepts_https_url() {
        FrontendProperties p = new FrontendProperties("https://app.jamoai.app");

        assertThat(p.baseUrl()).isEqualTo("https://app.jamoai.app");
    }

    @Test
    void accepts_http_url_for_local_development() {
        FrontendProperties p = new FrontendProperties("http://localhost:3000");

        assertThat(p.baseUrl()).isEqualTo("http://localhost:3000");
    }

    @Test
    void rejects_blank() {
        assertThatThrownBy(() -> new FrontendProperties(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejects_null() {
        assertThatThrownBy(() -> new FrontendProperties(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("baseUrl");
    }

    @Test
    void rejects_unsupported_scheme() {
        assertThatThrownBy(() -> new FrontendProperties("ftp://app.jamoai.app"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http");
    }

    @Test
    void rejects_no_scheme() {
        assertThatThrownBy(() -> new FrontendProperties("app.jamoai.app"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http");
    }
}
