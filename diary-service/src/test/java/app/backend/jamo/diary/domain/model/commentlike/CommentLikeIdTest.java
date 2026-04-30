package app.backend.jamo.diary.domain.model.commentlike;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentLikeIdTest {

    @Test
    void newId_generates_unique_uuid() {
        CommentLikeId a = CommentLikeId.newId();
        CommentLikeId b = CommentLikeId.newId();
        assertNotEquals(a, b);
        assertNotNull(a.value());
    }

    @Test
    void of_wraps_uuid() {
        UUID raw = UUID.randomUUID();
        assertEquals(raw, CommentLikeId.of(raw).value());
    }

    @Test
    void fromString_parses_valid_uuid() {
        UUID raw = UUID.randomUUID();
        CommentLikeId id = CommentLikeId.fromString(raw.toString());
        assertEquals(raw, id.value());
    }

    @Test
    void fromString_rejects_invalid_uuid() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> CommentLikeId.fromString("not-a-uuid"));
        assertTrue(ex.getMessage().contains("invalid CommentLikeId"));
    }

    @Test
    void rejects_null() {
        assertThrows(NullPointerException.class, () -> CommentLikeId.of(null));
        assertThrows(NullPointerException.class, () -> CommentLikeId.fromString(null));
        assertThrows(NullPointerException.class, () -> new CommentLikeId(null));
    }

    @Test
    void asString_round_trips() {
        CommentLikeId id = CommentLikeId.newId();
        assertEquals(id.value().toString(), id.asString());
    }

    @Test
    void equals_by_value() {
        UUID raw = UUID.randomUUID();
        assertEquals(CommentLikeId.of(raw), CommentLikeId.of(raw));
        assertEquals(CommentLikeId.of(raw).hashCode(), CommentLikeId.of(raw).hashCode());
    }
}
