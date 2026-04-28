package app.backend.jamo.diary.domain.model.sentencefeedback;

import app.backend.jamo.diary.domain.exception.InvalidSentenceTextException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentenceTextTest {

    @Nested
    class HappyPath {

        @Test
        void single_character_is_accepted() {
            assertEquals("a", new SentenceText("a").value());
        }

        @Test
        void exactly_50_code_points_is_accepted() {
            String exact50 = "a".repeat(50);
            assertEquals(exact50, new SentenceText(exact50).value());
        }

        @Test
        void korean_50_code_points_is_accepted() {
            // 가나다... 50자
            String korean50 = "가".repeat(50);
            assertDoesNotThrow(() -> new SentenceText(korean50));
        }

        @Test
        void emoji_counted_as_single_code_point() {
            // 😀 = 1 code point (BMP supplementary), 2 chars (surrogate pair)
            // String 50 code points = 100 chars when emoji 만
            String emoji50 = "😀".repeat(50);
            assertEquals(100, emoji50.length(), "sanity check — emoji is 2 chars");
            assertEquals(50, emoji50.codePointCount(0, emoji50.length()));
            assertDoesNotThrow(() -> new SentenceText(emoji50));
        }

        @Test
        void leading_trailing_whitespace_preserved_when_non_blank() {
            // trim 미적용 — 사용자 입력 보존
            String withSpaces = "  hello  ";
            assertEquals("  hello  ", new SentenceText(withSpaces).value());
        }
    }

    @Nested
    class Boundaries {

        @Test
        void exceeds_50_code_points_is_rejected() {
            String over50 = "a".repeat(51);
            InvalidSentenceTextException ex = assertThrows(
                InvalidSentenceTextException.class,
                () -> new SentenceText(over50)
            );
            assertTrue(ex.getMessage().contains("max 50"));
        }

        @Test
        void emoji_51_code_points_is_rejected() {
            String over50 = "😀".repeat(51);
            assertThrows(InvalidSentenceTextException.class, () -> new SentenceText(over50));
        }
    }

    @Nested
    class BlankRejection {

        @ParameterizedTest
        @NullSource
        void null_is_rejected(String invalid) {
            assertThrows(NullPointerException.class, () -> new SentenceText(invalid));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "  ", "\t", "\n", "\t\n  "})
        void blank_or_whitespace_only_is_rejected(String invalid) {
            assertThrows(InvalidSentenceTextException.class, () -> new SentenceText(invalid));
        }
    }
}
