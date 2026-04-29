package app.backend.jamo.diary.domain.model.diary;

import app.backend.jamo.diary.domain.exception.InvalidTagException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TagTest {

    @Test
    void accepts_within_bounds() {
        Tag t = new Tag("일상");
        assertEquals("일상", t.value());
    }

    @Test
    void rejects_null() {
        assertThrows(NullPointerException.class, () -> new Tag(null));
    }

    @Test
    void rejects_empty() {
        assertThrows(InvalidTagException.class, () -> new Tag(""));
    }

    @Test
    void rejects_whitespace_only() {
        assertThrows(InvalidTagException.class, () -> new Tag("   "));
    }

    @Test
    void rejects_over_max_code_points() {
        String over = "a".repeat(Tag.MAX_CODE_POINTS + 1);
        assertThrows(InvalidTagException.class, () -> new Tag(over));
    }

    @Test
    void accepts_at_max_code_points_boundary() {
        String boundary = "가".repeat(Tag.MAX_CODE_POINTS);
        Tag t = new Tag(boundary);
        assertEquals(boundary, t.value());
    }
}
