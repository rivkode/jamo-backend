package app.backend.jamo.diary.domain.model.diary;

import app.backend.jamo.diary.domain.exception.InvalidDiaryContentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiaryContentTest {

    @Test
    void accepts_within_bounds() {
        DiaryContent c = new DiaryContent("오늘 하루 좋았다");
        assertEquals("오늘 하루 좋았다", c.value());
    }

    @Test
    void rejects_null() {
        assertThrows(NullPointerException.class, () -> new DiaryContent(null));
    }

    @Test
    void rejects_empty() {
        assertThrows(InvalidDiaryContentException.class, () -> new DiaryContent(""));
    }

    @Test
    void rejects_whitespace_only() {
        assertThrows(InvalidDiaryContentException.class, () -> new DiaryContent("   \n\t "));
    }

    @Test
    void counts_emoji_as_single_code_point() {
        // 😊 = 1 code point (surrogate pair 2 char) — invariant 통과
        DiaryContent c = new DiaryContent("😊");
        assertEquals("😊", c.value());
    }

    @Test
    void emoji_at_max_code_points_boundary_is_accepted() {
        String boundary = "😊".repeat(DiaryContent.MAX_CODE_POINTS);
        // sanity — surrogate pair 라 char length 는 2x
        org.junit.jupiter.api.Assertions.assertEquals(2 * DiaryContent.MAX_CODE_POINTS, boundary.length());
        DiaryContent c = new DiaryContent(boundary);
        assertEquals(boundary, c.value());
    }

    @Test
    void emoji_over_max_code_points_is_rejected() {
        String over = "😊".repeat(DiaryContent.MAX_CODE_POINTS + 1);
        assertThrows(InvalidDiaryContentException.class, () -> new DiaryContent(over));
    }

    @Test
    void rejects_over_max_code_points() {
        String over = "a".repeat(DiaryContent.MAX_CODE_POINTS + 1);
        assertThrows(InvalidDiaryContentException.class, () -> new DiaryContent(over));
    }

    @Test
    void accepts_at_max_code_points_boundary() {
        String boundary = "가".repeat(DiaryContent.MAX_CODE_POINTS);
        DiaryContent c = new DiaryContent(boundary);
        assertEquals(boundary, c.value());
    }
}
