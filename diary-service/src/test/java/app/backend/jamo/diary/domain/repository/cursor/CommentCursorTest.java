package app.backend.jamo.diary.domain.repository.cursor;

import app.backend.jamo.diary.domain.model.comment.CommentId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommentCursorTest {

    @Test
    void holds_chronological_tuple() {
        Instant t = Instant.parse("2026-04-30T10:00:00Z");
        CommentId id = CommentId.newId();
        CommentCursor cursor = new CommentCursor(t, id);
        assertEquals(t, cursor.lastCreatedAt());
        assertEquals(id, cursor.lastCommentId());
    }

    @Test
    void rejects_null_lastCreatedAt() {
        assertThrows(NullPointerException.class,
            () -> new CommentCursor(null, CommentId.newId()));
    }

    @Test
    void rejects_null_lastCommentId() {
        Instant t = Instant.parse("2026-04-30T10:00:00Z");
        assertThrows(NullPointerException.class,
            () -> new CommentCursor(t, null));
    }

    @Test
    void equals_by_value() {
        Instant t = Instant.parse("2026-04-30T10:00:00Z");
        CommentId id = CommentId.newId();
        assertEquals(new CommentCursor(t, id), new CommentCursor(t, id));
    }
}
