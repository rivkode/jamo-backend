package app.backend.jamo.diary.domain.model.diarylike;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiaryLikeIdTest {

    @Test
    void newId_generates_unique_uuid() {
        assertNotEquals(DiaryLikeId.newId(), DiaryLikeId.newId());
    }

    @Test
    void fromString_parses_valid_uuid() {
        UUID raw = UUID.randomUUID();
        assertEquals(raw, DiaryLikeId.fromString(raw.toString()).value());
    }

    @Test
    void fromString_rejects_invalid_uuid() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> DiaryLikeId.fromString("invalid"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("invalid DiaryLikeId"));
    }

    @Test
    void rejects_null() {
        assertThrows(NullPointerException.class, () -> DiaryLikeId.of(null));
        assertThrows(NullPointerException.class, () -> DiaryLikeId.fromString(null));
        assertThrows(NullPointerException.class, () -> new DiaryLikeId(null));
    }

    @Test
    void equals_by_value() {
        UUID raw = UUID.randomUUID();
        assertEquals(DiaryLikeId.of(raw), DiaryLikeId.of(raw));
        assertEquals(DiaryLikeId.of(raw).hashCode(), DiaryLikeId.of(raw).hashCode());
    }
}
