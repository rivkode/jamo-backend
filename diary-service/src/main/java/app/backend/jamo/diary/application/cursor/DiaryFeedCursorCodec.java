package app.backend.jamo.diary.application.cursor;

import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.repository.cursor.PopularFeedCursor;
import app.backend.jamo.diary.domain.repository.cursor.RecentFeedCursor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/**
 * 피드 cursor 인코딩 / 디코딩 — base64 opaque 문자열.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §7.
 *
 * <p>형식 (sort 별 다름):
 * <ul>
 *   <li>RECENT: {@code "R|<lastCreatedAtIso>|<lastDiaryId>"}</li>
 *   <li>POPULAR: {@code "P|<lastLikeCount>|<lastCreatedAtIso>|<lastDiaryId>"}</li>
 * </ul>
 *
 * <p>외부 입장에선 base64 문자열 (opaque) — 내부 형식 변경 시 호환성 책임 X (cursor 는 단기 페이지네이션 용).
 *
 * <p>{@code lastCreatedAt} 의 표현은 {@link Instant#toString()} = ISO-8601 (예: {@code 2026-04-29T10:15:30Z}).
 * keyset 인덱스의 timestamp 정렬과 1:1 — 형식 변경 시 cursor 호환성 깨질 수 있어 표현 박제.
 *
 * <p>잘못된 cursor 입력은 {@link InvalidDiaryFeedCursorException} (Presentation 매핑: 400). cause 는 원본
 * 예외 (DateTimeParseException / IllegalArgumentException) 보존 — 로그에 raw cursor 노출 회피.
 */
public final class DiaryFeedCursorCodec {

    private static final char DELIMITER = '|';
    private static final String RECENT_PREFIX = "R";
    private static final String POPULAR_PREFIX = "P";

    private DiaryFeedCursorCodec() {
    }

    public static String encodeRecent(RecentFeedCursor cursor) {
        String raw = RECENT_PREFIX + DELIMITER + cursor.lastCreatedAt().toString() + DELIMITER
            + cursor.lastDiaryId().asString();
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static String encodePopular(PopularFeedCursor cursor) {
        String raw = POPULAR_PREFIX + DELIMITER + cursor.lastLikeCount() + DELIMITER
            + cursor.lastCreatedAt().toString() + DELIMITER
            + cursor.lastDiaryId().asString();
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static RecentFeedCursor decodeRecent(String encoded) {
        String[] parts = decodeAndSplit(encoded, RECENT_PREFIX, 3);
        try {
            Instant lastCreatedAt = Instant.parse(parts[1]);
            DiaryId lastDiaryId = DiaryId.of(UUID.fromString(parts[2]));
            return new RecentFeedCursor(lastCreatedAt, lastDiaryId);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new InvalidDiaryFeedCursorException("invalid RECENT cursor payload", e);
        }
    }

    public static PopularFeedCursor decodePopular(String encoded) {
        String[] parts = decodeAndSplit(encoded, POPULAR_PREFIX, 4);
        try {
            int lastLikeCount = Integer.parseInt(parts[1]);
            Instant lastCreatedAt = Instant.parse(parts[2]);
            DiaryId lastDiaryId = DiaryId.of(UUID.fromString(parts[3]));
            return new PopularFeedCursor(lastLikeCount, lastCreatedAt, lastDiaryId);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new InvalidDiaryFeedCursorException("invalid POPULAR cursor payload", e);
        }
    }

    private static String[] decodeAndSplit(String encoded, String expectedPrefix, int expectedParts) {
        if (encoded == null || encoded.isBlank()) {
            throw new InvalidDiaryFeedCursorException("cursor must not be blank");
        }
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new InvalidDiaryFeedCursorException("invalid base64 cursor", e);
        }
        String[] parts = raw.split("\\" + DELIMITER, -1);
        if (parts.length != expectedParts || !expectedPrefix.equals(parts[0])) {
            throw new InvalidDiaryFeedCursorException(
                "invalid cursor format (expected prefix=" + expectedPrefix
                    + ", parts=" + expectedParts + ")");
        }
        return parts;
    }
}
