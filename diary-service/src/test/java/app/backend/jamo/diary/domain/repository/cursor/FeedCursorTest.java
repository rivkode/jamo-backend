package app.backend.jamo.diary.domain.repository.cursor;

import app.backend.jamo.diary.domain.model.diary.DiaryId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeedCursorTest {

    private final Instant now = Instant.parse("2026-04-29T10:00:00Z");
    private final DiaryId id = DiaryId.newId();

    @Nested
    class Recent {

        @Test
        void accepts_valid() {
            RecentFeedCursor cursor = new RecentFeedCursor(now, id);
            assertEquals(now, cursor.lastCreatedAt());
            assertEquals(id, cursor.lastDiaryId());
        }

        @Test
        void rejects_null() {
            assertThrows(NullPointerException.class, () -> new RecentFeedCursor(null, id));
            assertThrows(NullPointerException.class, () -> new RecentFeedCursor(now, null));
        }

        @Test
        void equals_by_value() {
            assertEquals(new RecentFeedCursor(now, id), new RecentFeedCursor(now, id));
        }
    }

    @Nested
    class Popular {

        @Test
        void accepts_valid() {
            PopularFeedCursor cursor = new PopularFeedCursor(42, now, id);
            assertEquals(42, cursor.lastLikeCount());
            assertEquals(now, cursor.lastCreatedAt());
            assertEquals(id, cursor.lastDiaryId());
        }

        @Test
        void rejects_negative_like_count() {
            assertThrows(IllegalArgumentException.class,
                () -> new PopularFeedCursor(-1, now, id));
        }

        @Test
        void accepts_zero_like_count() {
            PopularFeedCursor cursor = new PopularFeedCursor(0, now, id);
            assertEquals(0, cursor.lastLikeCount());
        }

        @Test
        void rejects_null() {
            assertThrows(NullPointerException.class, () -> new PopularFeedCursor(0, null, id));
            assertThrows(NullPointerException.class, () -> new PopularFeedCursor(0, now, null));
        }

        @Test
        void equals_by_value() {
            assertEquals(new PopularFeedCursor(7, now, id), new PopularFeedCursor(7, now, id));
        }
    }
}
