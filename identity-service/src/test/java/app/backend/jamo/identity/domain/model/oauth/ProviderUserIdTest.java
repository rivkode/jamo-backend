package app.backend.jamo.identity.domain.model.oauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderUserIdTest {

    @Test
    void valid_value_is_accepted() {
        assertThat(new ProviderUserId("kakao-12345").value()).isEqualTo("kakao-12345");
    }

    @Test
    void blank_is_rejected() {
        assertThatThrownBy(() -> new ProviderUserId(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overly_long_is_rejected() {
        assertThatThrownBy(() -> new ProviderUserId("a".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
