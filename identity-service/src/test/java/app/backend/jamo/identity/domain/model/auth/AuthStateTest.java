package app.backend.jamo.identity.domain.model.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthStateTest {

    @Test
    void random_creates_uuid_like_state() {
        AuthState state = AuthState.random();

        assertThat(state.value()).hasSize(36);
    }

    @Test
    void blank_value_is_rejected() {
        assertThatThrownBy(() -> new AuthState(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
