package app.backend.jamo.diary.domain.model.diary;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiaryIdTest {

    @Test
    void newId_generates_unique_uuid() {
        DiaryId a = DiaryId.newId();
        DiaryId b = DiaryId.newId();
        assertNotEquals(a, b);
        assertNotNull(a.value());
    }

    @Test
    void of_wraps_uuid() {
        UUID raw = UUID.randomUUID();
        assertEquals(raw, DiaryId.of(raw).value());
    }

    @Test
    void fromString_parses_valid_uuid() {
        UUID raw = UUID.randomUUID();
        DiaryId id = DiaryId.fromString(raw.toString());
        assertEquals(raw, id.value());
    }

    @Test
    void fromString_rejects_invalid_uuid() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> DiaryId.fromString("not-a-uuid"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("invalid DiaryId"));
    }

    @Test
    void rejects_null() {
        assertThrows(NullPointerException.class, () -> DiaryId.of(null));
        assertThrows(NullPointerException.class, () -> DiaryId.fromString(null));
        assertThrows(NullPointerException.class, () -> new DiaryId(null));
    }

    @Test
    void asString_round_trips() {
        DiaryId id = DiaryId.newId();
        assertEquals(id.value().toString(), id.asString());
    }

    @Test
    void equals_by_value() {
        UUID raw = UUID.randomUUID();
        assertEquals(DiaryId.of(raw), DiaryId.of(raw));
        assertEquals(DiaryId.of(raw).hashCode(), DiaryId.of(raw).hashCode());
    }
}
