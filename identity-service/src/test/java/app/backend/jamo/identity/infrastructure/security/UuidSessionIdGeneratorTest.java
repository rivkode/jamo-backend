package app.backend.jamo.identity.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class UuidSessionIdGeneratorTest {

    private static final Pattern UUID_V4_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    private final UuidSessionIdGenerator generator = new UuidSessionIdGenerator();

    @Test
    void newSessionId_returns_uuid_v4_format() {
        String sid = generator.newSessionId();

        assertThat(sid).matches(UUID_V4_PATTERN);
        assertThat(UUID.fromString(sid).version()).isEqualTo(4);
    }

    @Test
    void newSessionId_does_not_collide_in_practice() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(generator.newSessionId());
        }
        assertThat(ids).hasSize(10_000);
    }
}
