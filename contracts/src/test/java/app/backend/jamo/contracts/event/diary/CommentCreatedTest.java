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

class CommentCreatedTest {

    @Test
    void valid_construction_assigns_all_fields() {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        CommentCreated event = new CommentCreated(eventId, now, "comment-1", "diary-1", "user-1");

        assertAll(
            () -> assertEquals(eventId, event.eventId()),
            () -> assertEquals(now, event.occurredAt()),
            () -> assertEquals("comment-1", event.commentId()),
            () -> assertEquals("diary-1", event.diaryId()),
            () -> assertEquals("user-1", event.userId())
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_eventId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new CommentCreated(invalid, Instant.now(), "c", "d", "u"));
    }

    @Test
    void null_occurredAt_is_rejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new CommentCreated(UUID.randomUUID().toString(), null, "c", "d", "u"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_commentId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new CommentCreated(UUID.randomUUID().toString(), Instant.now(), invalid, "d", "u"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_diaryId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new CommentCreated(UUID.randomUUID().toString(), Instant.now(), "c", invalid, "u"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_userId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new CommentCreated(UUID.randomUUID().toString(), Instant.now(), "c", "d", invalid));
    }
}
