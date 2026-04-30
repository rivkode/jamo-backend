package app.backend.jamo.diary.domain.model.commentlike;

import app.backend.jamo.diary.domain.model.comment.CommentId;
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

class CommentLikeTest {

    private final CommentId commentId = CommentId.newId();
    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-04-30T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @Test
    void create_assigns_new_id() {
        CommentLike like = CommentLike.create(commentId, userId, clock);
        assertAll(
            () -> assertNotNull(like.id()),
            () -> assertEquals(commentId, like.commentId()),
            () -> assertEquals(userId, like.userId()),
            () -> assertEquals(now, like.createdAt())
        );
    }

    @Test
    void reconstitute_preserves_id() {
        CommentLikeId id = CommentLikeId.newId();
        CommentLike like = CommentLike.reconstitute(id, commentId, userId, now);
        assertEquals(id, like.id());
    }

    @Test
    void rejects_null_required_fields() {
        assertThrows(NullPointerException.class, () -> CommentLike.create(null, userId, clock));
        assertThrows(NullPointerException.class, () -> CommentLike.create(commentId, null, clock));
        assertThrows(NullPointerException.class, () -> CommentLike.create(commentId, userId, null));
    }

    @Test
    void equals_by_id_only_symmetric() {
        CommentLikeId id = CommentLikeId.newId();
        CommentLike a = CommentLike.reconstitute(id, commentId, userId, now);
        CommentLike b = CommentLike.reconstitute(id, CommentId.newId(), UUID.randomUUID(), now.plusSeconds(60));
        assertAll(
            () -> assertEquals(a, b),
            () -> assertEquals(b, a, "symmetric"),
            () -> assertEquals(a.hashCode(), b.hashCode())
        );
    }

    @Test
    void equals_is_reflexive() {
        CommentLike a = CommentLike.create(commentId, userId, clock);
        assertEquals(a, a);
    }

    @Test
    void not_equals_null() {
        CommentLike a = CommentLike.create(commentId, userId, clock);
        assertNotEquals(null, a);
    }

    @Test
    void not_equals_different_id() {
        // 명시적으로 서로 다른 ID 부여 — 사양 가독성 (ID 가 다르면 equals false)
        CommentLike a = CommentLike.reconstitute(CommentLikeId.newId(), commentId, userId, now);
        CommentLike b = CommentLike.reconstitute(CommentLikeId.newId(), commentId, userId, now);
        assertFalse(a.equals(b));
    }
}
