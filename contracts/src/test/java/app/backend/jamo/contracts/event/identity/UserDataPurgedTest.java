package app.backend.jamo.contracts.event.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserDataPurgedTest {

    @Test
    void valid_construction_assigns_all_fields() {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        UserDataPurged event = new UserDataPurged(eventId, now, "user-42", "diary");

        assertAll(
            () -> assertEquals(eventId, event.eventId()),
            () -> assertEquals(now, event.occurredAt()),
            () -> assertEquals("user-42", event.userId()),
            () -> assertEquals("diary", event.sourceService())
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_eventId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new UserDataPurged(invalid, Instant.now(), "user-1", "diary"));
    }

    @Test
    void null_occurredAt_is_rejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new UserDataPurged(UUID.randomUUID().toString(), null, "user-1", "diary"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_userId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new UserDataPurged(UUID.randomUUID().toString(), Instant.now(), invalid, "diary"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_sourceService_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new UserDataPurged(UUID.randomUUID().toString(), Instant.now(), "user-1", invalid));
    }
}
