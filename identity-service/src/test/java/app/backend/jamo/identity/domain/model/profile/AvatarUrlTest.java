package app.backend.jamo.identity.domain.model.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarUrlTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com/a.png",
            "https://cdn.jamo.app/u/123/avatar.jpg",
            "https://example.com:8443/path?x=1",
            "https://kr.example.io/p"
    })
    void valid_http_or_https_url_is_accepted(String url) {
        assertThat(new AvatarUrl(url).value()).isEqualTo(url);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    void null_or_blank_is_rejected(String input) {
        assertThatThrownBy(() -> new AvatarUrl(input))
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/x.png",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "data:image/png;base64,AAAA"
    })
    void non_http_scheme_is_rejected(String url) {
        assertThatThrownBy(() -> new AvatarUrl(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void no_host_is_rejected() {
        assertThatThrownBy(() -> new AvatarUrl("https:///path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");
    }

    @Test
    void invalid_uri_syntax_is_rejected() {
        assertThatThrownBy(() -> new AvatarUrl("https://exa mple.com/spaces"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exact_max_length_is_accepted() {
        String prefix = "https://e.io/";
        String url = prefix + "a".repeat(AvatarUrl.MAX_LENGTH - prefix.length());

        AvatarUrl avatar = new AvatarUrl(url);

        assertThat(avatar.value()).hasSize(AvatarUrl.MAX_LENGTH);
    }

    @Test
    void overly_long_url_is_rejected() {
        String prefix = "https://e.io/";
        String url = prefix + "a".repeat(AvatarUrl.MAX_LENGTH - prefix.length() + 1);

        assertThatThrownBy(() -> new AvatarUrl(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("length");
    }

    @Test
    void url_with_user_info_is_rejected() {
        // phishing / credential leak 차단 — `https://user:pass@host/path`
        assertThatThrownBy(() -> new AvatarUrl("https://user:pass@example.com/a.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userInfo");
    }
}
