package app.backend.jamo.diary.application.cursor;

import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.repository.cursor.PopularFeedCursor;
import app.backend.jamo.diary.domain.repository.cursor.RecentFeedCursor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiaryFeedCursorCodecTest {

    private final Instant now = Instant.parse("2026-04-30T10:00:00Z");
    private final DiaryId id = DiaryId.newId();

    @Nested
    class Recent {

        @Test
        void encode_decode_round_trip() {
            RecentFeedCursor original = new RecentFeedCursor(now, id);
            String encoded = DiaryFeedCursorCodec.encodeRecent(original);
            RecentFeedCursor decoded = DiaryFeedCursorCodec.decodeRecent(encoded);
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        void blank_cursor_throws() {
            assertThatThrownBy(() -> DiaryFeedCursorCodec.decodeRecent(""))
                .isInstanceOf(InvalidDiaryFeedCursorException.class);
            assertThatThrownBy(() -> DiaryFeedCursorCodec.decodeRecent(null))
                .isInstanceOf(InvalidDiaryFeedCursorException.class);
        }

        @Test
        void invalid_base64_throws() {
            assertThatThrownBy(() -> DiaryFeedCursorCodec.decodeRecent("!!!not-base64!!!"))
                .isInstanceOf(InvalidDiaryFeedCursorException.class);
        }

        @Test
        void wrong_prefix_throws() {
            // POPULAR cursor 를 RECENT 로 디코드 시도
            String pop = DiaryFeedCursorCodec.encodePopular(new PopularFeedCursor(0, now, id));
            assertThatThrownBy(() -> DiaryFeedCursorCodec.decodeRecent(pop))
                .isInstanceOf(InvalidDiaryFeedCursorException.class);
        }
    }

    @Nested
    class Popular {

        @Test
        void encode_decode_round_trip() {
            PopularFeedCursor original = new PopularFeedCursor(42, now, id);
            String encoded = DiaryFeedCursorCodec.encodePopular(original);
            PopularFeedCursor decoded = DiaryFeedCursorCodec.decodePopular(encoded);
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        void wrong_prefix_throws() {
            String rec = DiaryFeedCursorCodec.encodeRecent(new RecentFeedCursor(now, id));
            assertThatThrownBy(() -> DiaryFeedCursorCodec.decodePopular(rec))
                .isInstanceOf(InvalidDiaryFeedCursorException.class);
        }

        @Test
        void zero_like_count_round_trip() {
            PopularFeedCursor original = new PopularFeedCursor(0, now, id);
            assertThat(DiaryFeedCursorCodec.decodePopular(DiaryFeedCursorCodec.encodePopular(original)))
                .isEqualTo(original);
        }
    }
}
