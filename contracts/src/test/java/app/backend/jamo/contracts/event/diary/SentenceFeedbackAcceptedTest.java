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

class SentenceFeedbackAcceptedTest {

    @Test
    void valid_construction_assigns_all_fields() {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        SentenceFeedbackAccepted event = new SentenceFeedbackAccepted(
            eventId, now, "feedback-1", "user-1", "suggestion-1"
        );

        assertAll(
            () -> assertEquals(eventId, event.eventId()),
            () -> assertEquals(now, event.occurredAt()),
            () -> assertEquals("feedback-1", event.feedbackId()),
            () -> assertEquals("user-1", event.userId()),
            () -> assertEquals("suggestion-1", event.suggestionId())
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_eventId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new SentenceFeedbackAccepted(invalid, Instant.now(), "feedback-1", "user-1", "suggestion-1"));
    }

    @Test
    void null_occurredAt_is_rejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new SentenceFeedbackAccepted(UUID.randomUUID().toString(), null, "feedback-1", "user-1", "suggestion-1"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_feedbackId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new SentenceFeedbackAccepted(UUID.randomUUID().toString(), Instant.now(), invalid, "user-1", "suggestion-1"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_userId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new SentenceFeedbackAccepted(UUID.randomUUID().toString(), Instant.now(), "feedback-1", invalid, "suggestion-1"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void null_or_blank_suggestionId_is_rejected(String invalid) {
        assertThrows(IllegalArgumentException.class,
            () -> new SentenceFeedbackAccepted(UUID.randomUUID().toString(), Instant.now(), "feedback-1", "user-1", invalid));
    }
}
