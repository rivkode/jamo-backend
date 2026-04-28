package app.backend.jamo.contracts.event.activity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActivityHappenedTest {

    @Test
    void valid_construction_assigns_all_fields() {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        ActivityHappened event = new ActivityHappened(eventId, now, "user-1", "diary.created", 10L);

        assertAll(
            () -> assertEquals(eventId, event.eventId()),
            () -> assertEquals(now, event.occurredAt()),
            () -> assertEquals("user-1", event.userId()),
            () -> assertEquals("diary.created", event.type()),
            () -> assertEquals(10L, event.points())
        );
    }

    @Test
    void negative_points_allowed_for_compensation() {
        ActivityHappened event = new ActivityHappened(
            UUID.randomUUID().toString(), Instant.now(), "user-1", "diary.deleted", -10L);

        assertEquals(-10L, event.points());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_eventId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new ActivityHappened(invalid, Instant.now(), "user-1", "diary.created", 10L));
    }

    @Test
    void null_occurredAt_is_rejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new ActivityHappened(UUID.randomUUID().toString(), null, "user-1", "diary.created", 10L));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_userId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new ActivityHappened(UUID.randomUUID().toString(), Instant.now(), invalid, "diary.created", 10L));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_type_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new ActivityHappened(UUID.randomUUID().toString(), Instant.now(), "user-1", invalid, 10L));
    }
}
