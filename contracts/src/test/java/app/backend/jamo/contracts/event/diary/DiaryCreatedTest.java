package app.backend.jamo.contracts.event.diary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiaryCreatedTest {

    @Test
    void valid_construction_assigns_all_fields() {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        DiaryCreated event = new DiaryCreated(eventId, now, "diary-1", "user-1");

        assertAll(
            () -> assertEquals(eventId, event.eventId()),
            () -> assertEquals(now, event.occurredAt()),
            () -> assertEquals("diary-1", event.diaryId()),
            () -> assertEquals("user-1", event.userId())
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_eventId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new DiaryCreated(invalid, Instant.now(), "diary-1", "user-1"));
    }

    @Test
    void null_occurredAt_is_rejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new DiaryCreated(UUID.randomUUID().toString(), null, "diary-1", "user-1"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_diaryId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new DiaryCreated(UUID.randomUUID().toString(), Instant.now(), invalid, "user-1"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_userId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new DiaryCreated(UUID.randomUUID().toString(), Instant.now(), "diary-1", invalid));
    }
}
