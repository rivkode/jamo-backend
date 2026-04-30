package app.backend.jamo.diary.domain.model.comment;

import app.backend.jamo.diary.domain.model.diary.DiaryId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentTest {

    private CommentId id;
    private DiaryId diaryId;
    private UUID author;
    private UUID otherUser;
    private CommentContent content;
    private Instant now;
    private Clock clock;

    @BeforeEach
    void setUp() {
        id = CommentId.newId();
        diaryId = DiaryId.newId();
        author = UUID.randomUUID();
        otherUser = UUID.randomUUID();
        content = new CommentContent("좋은 글이네요!");
        now = Instant.parse("2026-04-30T10:00:00Z");
        clock = Clock.fixed(now, ZoneOffset.UTC);
    }

    @Nested
    class Create {

        @Test
        void root_comment_with_zero_counter() {
            Comment c = Comment.create(id, diaryId, author, content, null, clock);
            assertAll(
                () -> assertEquals(id, c.id()),
                () -> assertEquals(diaryId, c.diaryId()),
                () -> assertEquals(author, c.authorId()),
                () -> assertEquals(content, c.content()),
                () -> assertEquals(Optional.empty(), c.parentId()),
                () -> assertTrue(c.isRoot()),
                () -> assertFalse(c.isReply()),
                () -> assertEquals(0, c.likeCount()),
                () -> assertEquals(now, c.createdAt())
            );
        }

        @Test
        void reply_with_parent_id() {
            CommentId parentId = CommentId.newId();
            Comment c = Comment.create(id, diaryId, author, content, parentId, clock);
            assertAll(
                () -> assertEquals(Optional.of(parentId), c.parentId()),
                () -> assertTrue(c.isReply()),
                () -> assertFalse(c.isRoot())
            );
        }

        @Test
        void rejects_null_required_fields() {
            assertThrows(NullPointerException.class,
                () -> Comment.create(null, diaryId, author, content, null, clock));
            assertThrows(NullPointerException.class,
                () -> Comment.create(id, null, author, content, null, clock));
            assertThrows(NullPointerException.class,
                () -> Comment.create(id, diaryId, null, content, null, clock));
            assertThrows(NullPointerException.class,
                () -> Comment.create(id, diaryId, author, null, null, clock));
            assertThrows(NullPointerException.class,
                () -> Comment.create(id, diaryId, author, content, null, null));
        }

        @Test
        void parentId_is_nullable() {
            // null = 루트 댓글 — invariant 위반 아님
            Comment c = Comment.create(id, diaryId, author, content, null, clock);
            assertEquals(Optional.empty(), c.parentId());
        }
    }

    @Nested
    class Reconstitute {

        @Test
        void preserves_existing_counter() {
            Comment c = Comment.reconstitute(id, diaryId, author, content, null, 7, now);
            assertAll(
                () -> assertEquals(7, c.likeCount()),
                () -> assertTrue(c.isRoot())
            );
        }

        @Test
        void preserves_parent_id() {
            CommentId parentId = CommentId.newId();
            Comment c = Comment.reconstitute(id, diaryId, author, content, parentId, 0, now);
            assertEquals(Optional.of(parentId), c.parentId());
        }

        @Test
        void rejects_negative_counter() {
            assertThrows(IllegalArgumentException.class,
                () -> Comment.reconstitute(id, diaryId, author, content, null, -1, now));
        }
    }

    @Nested
    class LikeCounter {

        @Test
        void onLikeAdded_then_onLikeRemoved() {
            Comment c = Comment.create(id, diaryId, author, content, null, clock);
            c.onLikeAdded();
            c.onLikeAdded();
            assertEquals(2, c.likeCount());
            c.onLikeRemoved();
            assertEquals(1, c.likeCount());
        }

        @Test
        void onLikeRemoved_below_zero_throws() {
            Comment c = Comment.create(id, diaryId, author, content, null, clock);
            assertThrows(IllegalStateException.class, c::onLikeRemoved);
        }

        @Test
        void onLikeRemoved_at_zero_throws() {
            Comment c = Comment.create(id, diaryId, author, content, null, clock);
            c.onLikeAdded();
            c.onLikeRemoved();
            assertEquals(0, c.likeCount());
            assertThrows(IllegalStateException.class, c::onLikeRemoved);
        }
    }

    @Nested
    class IsOwnedBy {

        @Test
        void true_only_for_author() {
            Comment c = Comment.create(id, diaryId, author, content, null, clock);
            assertTrue(c.isOwnedBy(author));
            assertFalse(c.isOwnedBy(otherUser));
        }

        @Test
        void rejects_null_user() {
            Comment c = Comment.create(id, diaryId, author, content, null, clock);
            assertThrows(NullPointerException.class, () -> c.isOwnedBy(null));
        }
    }

    @Nested
    class DepthHelpers {

        @Test
        void root_isRoot_true_isReply_false() {
            Comment c = Comment.create(id, diaryId, author, content, null, clock);
            assertTrue(c.isRoot());
            assertFalse(c.isReply());
        }

        @Test
        void reply_isReply_true_isRoot_false() {
            Comment c = Comment.create(id, diaryId, author, content, CommentId.newId(), clock);
            assertTrue(c.isReply());
            assertFalse(c.isRoot());
        }

        @Test
        void parentId_returns_optional() {
            CommentId parent = CommentId.newId();
            Comment root = Comment.create(id, diaryId, author, content, null, clock);
            Comment reply = Comment.create(CommentId.newId(), diaryId, author, content, parent, clock);
            assertEquals(Optional.empty(), root.parentId());
            assertEquals(Optional.of(parent), reply.parentId());
        }
    }

    @Nested
    class Equality {

        @Test
        void equals_by_id_only() {
            Comment c1 = Comment.create(id, diaryId, author, content, null, clock);
            Comment c2 = Comment.reconstitute(id, DiaryId.newId(), otherUser,
                new CommentContent("다른 본문"), CommentId.newId(), 99, now);
            assertAll(
                () -> assertEquals(c1, c2),
                () -> assertEquals(c2, c1, "symmetric"),
                () -> assertEquals(c1.hashCode(), c2.hashCode())
            );
        }

        @Test
        void equals_is_reflexive() {
            Comment c = Comment.create(id, diaryId, author, content, null, clock);
            assertEquals(c, c);
        }

        @Test
        void not_equals_different_id() {
            Comment c1 = Comment.create(CommentId.newId(), diaryId, author, content, null, clock);
            Comment c2 = Comment.create(CommentId.newId(), diaryId, author, content, null, clock);
            assertNotEquals(c1, c2);
        }

        @Test
        void not_equals_null() {
            Comment c = Comment.create(id, diaryId, author, content, null, clock);
            assertNotEquals(null, c);
        }

        @Test
        void parentId_does_not_affect_equals() {
            // 양방향 검증 — parentId 가 root↔reply 어느 쪽이든 ID 같으면 equals (mutable/구조 필드 미포함)
            CommentId parentA = CommentId.newId();
            Comment rootThenReply = Comment.reconstitute(id, diaryId, author, content, null, 0, now);
            Comment replyThenRoot = Comment.reconstitute(id, diaryId, author, content, parentA, 5, now);
            assertEquals(rootThenReply, replyThenRoot);
            assertEquals(replyThenRoot, rootThenReply, "symmetric");
        }
    }
}
