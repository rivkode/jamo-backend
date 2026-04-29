package app.backend.jamo.diary.domain.model.diary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiaryTest {

    private DiaryId id;
    private UUID author;
    private UUID otherUser;
    private DiaryContent content;
    private Tags tags;
    private ImageUrls images;
    private Instant now;
    private Clock clock;

    @BeforeEach
    void setUp() {
        id = DiaryId.newId();
        author = UUID.randomUUID();
        otherUser = UUID.randomUUID();
        content = new DiaryContent("오늘 산책");
        tags = Tags.ofStrings(List.of("일상"));
        images = ImageUrls.empty();
        now = Instant.parse("2026-04-29T10:00:00Z");
        clock = Clock.fixed(now, ZoneOffset.UTC);
    }

    @Nested
    class Create {

        @Test
        void initializes_with_zero_counters() {
            Diary d = Diary.create(id, author, content, images, tags, Visibility.PUBLIC, clock);
            assertAll(
                () -> assertEquals(id, d.id()),
                () -> assertEquals(author, d.authorId()),
                () -> assertEquals(content, d.content()),
                () -> assertEquals(images, d.images()),
                () -> assertEquals(tags, d.tags()),
                () -> assertEquals(Visibility.PUBLIC, d.visibility()),
                () -> assertEquals(0, d.likeCount()),
                () -> assertEquals(0, d.commentCount()),
                () -> assertEquals(now, d.createdAt())
            );
        }

        @Test
        void rejects_null_required_fields() {
            assertThrows(NullPointerException.class,
                () -> Diary.create(null, author, content, images, tags, Visibility.PUBLIC, clock));
            assertThrows(NullPointerException.class,
                () -> Diary.create(id, null, content, images, tags, Visibility.PUBLIC, clock));
            assertThrows(NullPointerException.class,
                () -> Diary.create(id, author, null, images, tags, Visibility.PUBLIC, clock));
            assertThrows(NullPointerException.class,
                () -> Diary.create(id, author, content, null, tags, Visibility.PUBLIC, clock));
            assertThrows(NullPointerException.class,
                () -> Diary.create(id, author, content, images, null, Visibility.PUBLIC, clock));
            assertThrows(NullPointerException.class,
                () -> Diary.create(id, author, content, images, tags, null, clock));
            assertThrows(NullPointerException.class,
                () -> Diary.create(id, author, content, images, tags, Visibility.PUBLIC, null));
        }
    }

    @Nested
    class Reconstitute {

        @Test
        void preserves_existing_counters() {
            Diary d = Diary.reconstitute(id, author, content, images, tags, Visibility.PRIVATE, 7, 3, now);
            assertAll(
                () -> assertEquals(7, d.likeCount()),
                () -> assertEquals(3, d.commentCount()),
                () -> assertEquals(Visibility.PRIVATE, d.visibility())
            );
        }

        @Test
        void rejects_negative_counters() {
            assertThrows(IllegalArgumentException.class,
                () -> Diary.reconstitute(id, author, content, images, tags, Visibility.PUBLIC, -1, 0, now));
            assertThrows(IllegalArgumentException.class,
                () -> Diary.reconstitute(id, author, content, images, tags, Visibility.PUBLIC, 0, -1, now));
        }
    }

    @Nested
    class LikeCounter {

        @Test
        void onLikeAdded_then_onLikeRemoved() {
            Diary d = Diary.create(id, author, content, images, tags, Visibility.PUBLIC, clock);
            d.onLikeAdded();
            d.onLikeAdded();
            assertEquals(2, d.likeCount());
            d.onLikeRemoved();
            assertEquals(1, d.likeCount());
        }

        @Test
        void onLikeRemoved_below_zero_throws() {
            Diary d = Diary.create(id, author, content, images, tags, Visibility.PUBLIC, clock);
            assertThrows(IllegalStateException.class, d::onLikeRemoved);
        }
    }

    @Nested
    class IsAccessibleBy {

        @Test
        void public_is_accessible_by_anyone() {
            Diary d = Diary.create(id, author, content, images, tags, Visibility.PUBLIC, clock);
            assertAll(
                () -> assertTrue(d.isAccessibleBy(author)),
                () -> assertTrue(d.isAccessibleBy(otherUser))
            );
        }

        @Test
        void private_grants_access_to_author() {
            Diary d = Diary.create(id, author, content, images, tags, Visibility.PRIVATE, clock);
            assertTrue(d.isAccessibleBy(author));
        }

        @Test
        void private_denies_access_to_others() {
            Diary d = Diary.create(id, author, content, images, tags, Visibility.PRIVATE, clock);
            assertFalse(d.isAccessibleBy(otherUser));
        }

        @Test
        void rejects_null_user() {
            Diary d = Diary.create(id, author, content, images, tags, Visibility.PUBLIC, clock);
            assertThrows(NullPointerException.class, () -> d.isAccessibleBy(null));
        }
    }

    @Nested
    class IsOwnedBy {

        @Test
        void true_only_for_author() {
            Diary d = Diary.create(id, author, content, images, tags, Visibility.PUBLIC, clock);
            assertTrue(d.isOwnedBy(author));
            assertFalse(d.isOwnedBy(otherUser));
        }

        @Test
        void public_does_not_grant_ownership() {
            Diary d = Diary.create(id, author, content, images, tags, Visibility.PUBLIC, clock);
            assertFalse(d.isOwnedBy(otherUser));
        }
    }

    @Nested
    class Equality {

        @Test
        void equals_by_id_only() {
            Diary d1 = Diary.create(id, author, content, images, tags, Visibility.PUBLIC, clock);
            Diary d2 = Diary.reconstitute(id, author, content, images, tags, Visibility.PRIVATE, 99, 9, now);
            assertAll(
                () -> assertEquals(d1, d2),
                () -> assertEquals(d2, d1, "symmetric"),
                () -> assertEquals(d1.hashCode(), d2.hashCode())
            );
        }

        @Test
        void equals_is_reflexive() {
            Diary d = Diary.create(id, author, content, images, tags, Visibility.PUBLIC, clock);
            assertEquals(d, d);
        }

        @Test
        void not_equals_different_id() {
            Diary d1 = Diary.create(DiaryId.newId(), author, content, images, tags, Visibility.PUBLIC, clock);
            Diary d2 = Diary.create(DiaryId.newId(), author, content, images, tags, Visibility.PUBLIC, clock);
            assertFalse(d1.equals(d2));
        }

        @Test
        void not_equals_null() {
            Diary d = Diary.create(id, author, content, images, tags, Visibility.PUBLIC, clock);
            assertFalse(d.equals(null));
        }
    }
}
