package app.backend.jamo.diary.domain.model.sentencefeedback;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SuggestionIdTest {

    @Test
    void newId_generates_random_unique_id() {
        SuggestionId a = SuggestionId.newId();
        SuggestionId b = SuggestionId.newId();
        assertNotNull(a.value());
        assertNotEquals(a, b);
    }

    @Test
    void of_wraps_uuid() {
        UUID uuid = UUID.randomUUID();
        SuggestionId id = SuggestionId.of(uuid);
        assertEquals(uuid, id.value());
    }

    @Test
    void fromString_parses_valid_uuid() {
        UUID uuid = UUID.randomUUID();
        SuggestionId id = SuggestionId.fromString(uuid.toString());
        assertEquals(uuid, id.value());
    }

    @Test
    void fromString_rejects_invalid_uuid() {
        assertThrows(IllegalArgumentException.class,
            () -> SuggestionId.fromString("not-a-uuid"));
    }

    @ParameterizedTest
    @NullSource
    void null_uuid_is_rejected(UUID invalid) {
        assertThrows(NullPointerException.class, () -> SuggestionId.of(invalid));
    }

    @Test
    void asString_returns_uuid_string() {
        UUID uuid = UUID.randomUUID();
        assertEquals(uuid.toString(), SuggestionId.of(uuid).asString());
    }

    @Test
    void equality_is_value_based() {
        UUID uuid = UUID.randomUUID();
        assertEquals(SuggestionId.of(uuid), SuggestionId.of(uuid));
    }
}
