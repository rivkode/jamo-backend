package app.backend.jamo.diary.domain.model.diarylike;

import app.backend.jamo.diary.domain.model.diary.DiaryId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiaryLikeTest {

    private final DiaryId diaryId = DiaryId.newId();
    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-04-29T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @Test
    void create_assigns_new_id() {
        DiaryLike like = DiaryLike.create(diaryId, userId, clock);
        assertAll(
            () -> assertNotNull(like.id()),
            () -> assertEquals(diaryId, like.diaryId()),
            () -> assertEquals(userId, like.userId()),
            () -> assertEquals(now, like.createdAt())
        );
    }

    @Test
    void reconstitute_preserves_id() {
        DiaryLikeId id = DiaryLikeId.newId();
        DiaryLike like = DiaryLike.reconstitute(id, diaryId, userId, now);
        assertEquals(id, like.id());
    }

    @Test
    void rejects_null_required_fields() {
        assertThrows(NullPointerException.class, () -> DiaryLike.create(null, userId, clock));
        assertThrows(NullPointerException.class, () -> DiaryLike.create(diaryId, null, clock));
        assertThrows(NullPointerException.class, () -> DiaryLike.create(diaryId, userId, null));
    }

    @Test
    void equals_by_id_only_symmetric() {
        DiaryLikeId id = DiaryLikeId.newId();
        DiaryLike a = DiaryLike.reconstitute(id, diaryId, userId, now);
        DiaryLike b = DiaryLike.reconstitute(id, DiaryId.newId(), UUID.randomUUID(), now.plusSeconds(60));
        assertAll(
            () -> assertEquals(a, b),
            () -> assertEquals(b, a, "symmetric"),
            () -> assertEquals(a.hashCode(), b.hashCode())
        );
    }

    @Test
    void equals_is_reflexive() {
        DiaryLike a = DiaryLike.create(diaryId, userId, clock);
        assertEquals(a, a);
    }

    @Test
    void not_equals_null() {
        DiaryLike a = DiaryLike.create(diaryId, userId, clock);
        assertNotEquals(null, a);
    }

    @Test
    void not_equals_different_id() {
        DiaryLike a = DiaryLike.create(diaryId, userId, clock);
        DiaryLike b = DiaryLike.create(diaryId, userId, clock);
        assertFalse(a.equals(b));
    }
}
