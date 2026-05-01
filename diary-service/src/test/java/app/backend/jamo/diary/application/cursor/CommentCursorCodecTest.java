package app.backend.jamo.diary.application.cursor;

import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.repository.cursor.CommentCursor;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentCursorCodecTest {

    private static final Instant NOW = Instant.parse("2026-04-30T10:15:30Z");

    @Test
    void round_trip_preserves_cursor() {
        CommentCursor cursor = new CommentCursor(NOW, CommentId.newId());
        String encoded = CommentCursorCodec.encode(cursor);
        CommentCursor decoded = CommentCursorCodec.decode(encoded);
        assertThat(decoded).isEqualTo(cursor);
    }

    @Test
    void encoded_is_base64_url_safe() {
        CommentCursor cursor = new CommentCursor(NOW, CommentId.newId());
        String encoded = CommentCursorCodec.encode(cursor);
        // url-safe alphabet: [A-Za-z0-9_-]
        assertThat(encoded).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void decode_rejects_blank() {
        assertThatThrownBy(() -> CommentCursorCodec.decode(null))
            .isInstanceOf(InvalidCommentCursorException.class)
            .hasMessageContaining("blank");
        assertThatThrownBy(() -> CommentCursorCodec.decode(""))
            .isInstanceOf(InvalidCommentCursorException.class);
        assertThatThrownBy(() -> CommentCursorCodec.decode("   "))
            .isInstanceOf(InvalidCommentCursorException.class);
    }

    @Test
    void decode_rejects_invalid_base64() {
        assertThatThrownBy(() -> CommentCursorCodec.decode("***not-base64***"))
            .isInstanceOf(InvalidCommentCursorException.class)
            .hasMessageContaining("base64")
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_rejects_wrong_prefix() {
        // 다른 prefix (R = RECENT, P = POPULAR) 로 인코딩된 cursor 는 거부
        String wrongPrefix = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("R|" + NOW + "|" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> CommentCursorCodec.decode(wrongPrefix))
            .isInstanceOf(InvalidCommentCursorException.class)
            .hasMessageContaining("prefix");
    }

    @Test
    void decode_rejects_wrong_part_count() {
        String wrongParts = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("C|" + NOW).getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> CommentCursorCodec.decode(wrongParts))
            .isInstanceOf(InvalidCommentCursorException.class)
            .hasMessageContaining("parts");
    }

    @Test
    void decode_rejects_invalid_instant() {
        String badInstant = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("C|not-a-date|" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> CommentCursorCodec.decode(badInstant))
            .isInstanceOf(InvalidCommentCursorException.class)
            .hasMessageContaining("payload");
    }

    @Test
    void decode_rejects_invalid_uuid() {
        String badUuid = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("C|" + NOW + "|not-a-uuid").getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> CommentCursorCodec.decode(badUuid))
            .isInstanceOf(InvalidCommentCursorException.class)
            .hasMessageContaining("payload");
    }

    @Test
    void cause_preserved_on_payload_failure() {
        String badInstant = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("C|not-a-date|" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> CommentCursorCodec.decode(badInstant))
            .hasCauseInstanceOf(java.time.format.DateTimeParseException.class);
    }

    @Test
    void encode_produces_stable_output_for_same_input() {
        CommentId id = CommentId.newId();
        CommentCursor cursor = new CommentCursor(NOW, id);
        assertThat(CommentCursorCodec.encode(cursor))
            .isEqualTo(CommentCursorCodec.encode(cursor));
    }
}
