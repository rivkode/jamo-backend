package app.backend.jamo.identity.domain.model.auth;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationCodeGeneratorTest {

    @Test
    void generated_code_is_url_safe_and_long_enough() {
        AuthorizationCodeGenerator generator = new AuthorizationCodeGenerator(new SecureRandom());

        String code = generator.generate();

        assertThat(code).matches("^[A-Za-z0-9_-]+$");
        assertThat(code.length()).isGreaterThanOrEqualTo(43);
    }

    @Test
    void successive_calls_produce_different_codes() {
        AuthorizationCodeGenerator generator = new AuthorizationCodeGenerator(new SecureRandom());

        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).isNotEqualTo(second);
    }
}
