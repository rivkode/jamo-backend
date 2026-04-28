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

class ChatGeneratedTest {

    @Test
    void valid_construction_assigns_all_fields() {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        ChatGenerated event = new ChatGenerated(eventId, now, "chat-1", "user-1", "room-1");

        assertAll(
            () -> assertEquals(eventId, event.eventId()),
            () -> assertEquals(now, event.occurredAt()),
            () -> assertEquals("chat-1", event.chatId()),
            () -> assertEquals("user-1", event.userId()),
            () -> assertEquals("room-1", event.roomId())
        );
    }

    @Test
    void empty_roomId_is_allowed_for_one_to_one_chat() {
        ChatGenerated event = new ChatGenerated(
            UUID.randomUUID().toString(), Instant.now(), "chat-1", "user-1", "");

        assertEquals("", event.roomId());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_eventId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new ChatGenerated(invalid, Instant.now(), "c", "u", ""));
    }

    @Test
    void null_occurredAt_is_rejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new ChatGenerated(UUID.randomUUID().toString(), null, "c", "u", ""));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_chatId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new ChatGenerated(UUID.randomUUID().toString(), Instant.now(), invalid, "u", ""));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_userId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new ChatGenerated(UUID.randomUUID().toString(), Instant.now(), "c", invalid, ""));
    }

    @Test
    void null_roomId_is_rejected() {
        // roomId 만 다른 필드와 다르게 빈 문자열 허용 (1:1 chat). null 만 거부.
        assertThrows(IllegalArgumentException.class,
            () -> new ChatGenerated(UUID.randomUUID().toString(), Instant.now(), "c", "u", null));
    }
}
