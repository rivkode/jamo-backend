package app.backend.jamo.identity.domain.model.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @Test
    void valid_email_is_accepted() {
        assertThat(new Email("test@jamoai.app").value()).isEqualTo("test@jamoai.app");
    }

    @Test
    void missing_at_sign_is_rejected() {
        assertThatThrownBy(() -> new Email("invalid")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missing_domain_dot_is_rejected() {
        assertThatThrownBy(() -> new Email("a@b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overly_long_email_is_rejected() {
        String local = "a".repeat(250);
        assertThatThrownBy(() -> new Email(local + "@b.cd")).isInstanceOf(IllegalArgumentException.class);
    }
}
