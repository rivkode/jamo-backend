package app.backend.jamo.diary.application.cursor;

import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.repository.cursor.CommentCursor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/**
 * 댓글 cursor 인코딩 / 디코딩 — base64 opaque 문자열 ({@code DiaryFeedCursorCodec} 정합).
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §6 (chronological 단일 정렬 — cursor opaque).
 *
 * <p>형식: {@code "C|<lastCreatedAtIso>|<lastCommentId>"} (단일 prefix, 정렬은 chronological asc 한 종류).
 *
 * <p>외부 입장에선 base64 문자열 (opaque) — 내부 형식 변경 시 호환성 책임 X.
 *
 * <p>{@code lastCreatedAt} 의 표현은 {@link Instant#toString()} = ISO-8601. keyset 인덱스의 timestamp 정렬과 1:1.
 *
 * <p>잘못된 cursor 입력은 {@link InvalidCommentCursorException} (Presentation 매핑: 400). cause 는 원본
 * 예외 ({@code DateTimeParseException} / {@code IllegalArgumentException}) 보존 — 로그에 raw cursor 노출 회피.
 */
public final class CommentCursorCodec {

    private static final char DELIMITER = '|';
    private static final String PREFIX = "C";
    private static final int EXPECTED_PARTS = 3;

    private CommentCursorCodec() {
    }

    public static String encode(CommentCursor cursor) {
        String raw = PREFIX + DELIMITER + cursor.lastCreatedAt().toString() + DELIMITER
            + cursor.lastCommentId().asString();
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static CommentCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new InvalidCommentCursorException("cursor must not be blank");
        }
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new InvalidCommentCursorException("invalid base64 cursor", e);
        }
        String[] parts = raw.split("\\" + DELIMITER, -1);
        if (parts.length != EXPECTED_PARTS || !PREFIX.equals(parts[0])) {
            throw new InvalidCommentCursorException(
                "invalid cursor format (expected prefix=" + PREFIX
                    + ", parts=" + EXPECTED_PARTS + ")");
        }
        try {
            Instant lastCreatedAt = Instant.parse(parts[1]);
            CommentId lastCommentId = CommentId.of(UUID.fromString(parts[2]));
            return new CommentCursor(lastCreatedAt, lastCommentId);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new InvalidCommentCursorException("invalid cursor payload", e);
        }
    }
}
