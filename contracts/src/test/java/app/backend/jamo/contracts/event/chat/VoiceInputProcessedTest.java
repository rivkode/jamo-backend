package app.backend.jamo.contracts.event.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoiceInputProcessedTest {

    @Test
    void valid_construction_assigns_all_fields() {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        VoiceInputProcessed event = new VoiceInputProcessed(eventId, now, "chat-1", "user-1", 1500L);

        assertAll(
            () -> assertEquals(eventId, event.eventId()),
            () -> assertEquals(now, event.occurredAt()),
            () -> assertEquals("chat-1", event.chatId()),
            () -> assertEquals("user-1", event.userId()),
            () -> assertEquals(1500L, event.durationMs())
        );
    }

    @Test
    void zero_durationMs_is_allowed() {
        VoiceInputProcessed event = new VoiceInputProcessed(
            UUID.randomUUID().toString(), Instant.now(), "chat-1", "user-1", 0L);

        assertEquals(0L, event.durationMs());
    }

    @Test
    void negative_durationMs_is_rejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new VoiceInputProcessed(UUID.randomUUID().toString(), Instant.now(), "c", "u", -1L));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_eventId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new VoiceInputProcessed(invalid, Instant.now(), "c", "u", 0L));
    }

    @Test
    void null_occurredAt_is_rejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new VoiceInputProcessed(UUID.randomUUID().toString(), null, "c", "u", 0L));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_chatId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new VoiceInputProcessed(UUID.randomUUID().toString(), Instant.now(), invalid, "u", 0L));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_userId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new VoiceInputProcessed(UUID.randomUUID().toString(), Instant.now(), "c", invalid, 0L));
    }
}
