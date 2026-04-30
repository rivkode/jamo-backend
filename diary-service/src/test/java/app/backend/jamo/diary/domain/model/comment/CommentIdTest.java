package app.backend.jamo.diary.domain.model.comment;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentIdTest {

    @Test
    void newId_generates_unique_uuid() {
        CommentId a = CommentId.newId();
        CommentId b = CommentId.newId();
        assertNotEquals(a, b);
        assertNotNull(a.value());
    }

    @Test
    void of_wraps_uuid() {
        UUID raw = UUID.randomUUID();
        assertEquals(raw, CommentId.of(raw).value());
    }

    @Test
    void fromString_parses_valid_uuid() {
        UUID raw = UUID.randomUUID();
        CommentId id = CommentId.fromString(raw.toString());
        assertEquals(raw, id.value());
    }

    @Test
    void fromString_rejects_invalid_uuid() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> CommentId.fromString("not-a-uuid"));
        assertTrue(ex.getMessage().contains("invalid CommentId"));
    }

    @Test
    void rejects_null() {
        assertThrows(NullPointerException.class, () -> CommentId.of(null));
        assertThrows(NullPointerException.class, () -> CommentId.fromString(null));
        assertThrows(NullPointerException.class, () -> new CommentId(null));
    }

    @Test
    void asString_round_trips() {
        CommentId id = CommentId.newId();
        assertEquals(id.value().toString(), id.asString());
    }

    @Test
    void equals_by_value() {
        UUID raw = UUID.randomUUID();
        assertEquals(CommentId.of(raw), CommentId.of(raw));
        assertEquals(CommentId.of(raw).hashCode(), CommentId.of(raw).hashCode());
    }
}
