package app.backend.jamo.diary.domain.model.diary;

import app.backend.jamo.diary.domain.exception.InvalidLineCountException;
import app.backend.jamo.diary.domain.exception.InvalidLineLengthException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiaryLinesTest {

    @Nested
    class Valid {

        @Test
        void exactly_three_lines_accepted() {
            DiaryLines lines = new DiaryLines(List.of("첫 줄", "둘째 줄", "셋째 줄"));
            assertEquals(List.of("첫 줄", "둘째 줄", "셋째 줄"), lines.values());
        }

        @Test
        void each_line_at_max_200_code_points_accepted() {
            String line200 = "가".repeat(200);
            assertDoesNotThrow(() -> new DiaryLines(List.of(line200, "b", "c")));
        }

        @Test
        void emoji_counted_as_single_code_point() {
            // 😀 = 1 code point, 2 chars. 200 cp = 400 chars 까지 허용.
            String emoji200 = "😀".repeat(200);
            assertEquals(200, emoji200.codePointCount(0, emoji200.length()));
            assertDoesNotThrow(() -> new DiaryLines(List.of(emoji200, "b", "c")));
        }

        @Test
        void values_are_immutable_copy() {
            var mutable = new java.util.ArrayList<>(List.of("a", "b", "c"));
            DiaryLines lines = new DiaryLines(mutable);
            mutable.set(0, "변조");
            assertEquals("a", lines.values().get(0));  // 원본 변경 영향 없음 (List.copyOf)
        }
    }

    @Nested
    class LineCount {

        @Test
        void two_lines_rejected_with_InvalidLineCount() {
            InvalidLineCountException ex = assertThrows(InvalidLineCountException.class,
                () -> new DiaryLines(List.of("첫", "둘")));
            assertTrue(ex.getMessage().contains("exactly 3"));
        }

        @Test
        void four_lines_rejected_with_InvalidLineCount() {
            assertThrows(InvalidLineCountException.class,
                () -> new DiaryLines(List.of("1", "2", "3", "4")));
        }

        @Test
        void empty_list_rejected_with_InvalidLineCount() {
            assertThrows(InvalidLineCountException.class, () -> new DiaryLines(List.of()));
        }

        @Test
        void count_checked_before_length_when_both_violated() {
            // ddd-architect: 개수 먼저 — 2줄(개수 위반) + 첫 줄 blank(길이 위반) 동시 → InvalidLineCount(422) 우선.
            assertThrows(InvalidLineCountException.class,
                () -> new DiaryLines(List.of("  ", "둘")));
        }
    }

    @Nested
    class LineLength {

        @Test
        void blank_line_rejected_with_InvalidLineLength() {
            assertThrows(InvalidLineLengthException.class,
                () -> new DiaryLines(List.of("정상", "  ", "정상")));
        }

        @Test
        void empty_line_rejected_with_InvalidLineLength() {
            assertThrows(InvalidLineLengthException.class,
                () -> new DiaryLines(List.of("정상", "", "정상")));
        }

        @Test
        void line_over_200_code_points_rejected_with_InvalidLineLength() {
            String over200 = "a".repeat(201);
            InvalidLineLengthException ex = assertThrows(InvalidLineLengthException.class,
                () -> new DiaryLines(List.of("정상", over200, "정상")));
            assertTrue(ex.getMessage().contains("max 200"));
        }

        @Test
        void null_element_rejected_with_InvalidLineLength() {
            assertThrows(InvalidLineLengthException.class,
                () -> new DiaryLines(Arrays.asList("정상", null, "정상")));
        }
    }

    @Test
    void null_values_rejected_with_NPE() {
        assertThrows(NullPointerException.class, () -> new DiaryLines(null));
    }
}
