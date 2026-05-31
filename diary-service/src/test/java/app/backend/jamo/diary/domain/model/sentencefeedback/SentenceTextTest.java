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
        void exactly_200_code_points_is_accepted() {
            String exact200 = "a".repeat(200);
            assertEquals(exact200, new SentenceText(exact200).value());
        }

        @Test
        void korean_200_code_points_is_accepted() {
            String korean200 = "가".repeat(200);
            assertDoesNotThrow(() -> new SentenceText(korean200));
        }

        @Test
        void emoji_counted_as_single_code_point() {
            // 😀 = 1 code point (BMP supplementary), 2 chars (surrogate pair)
            // String 50 code points = 100 chars when emoji 만
            String emoji200 = "😀".repeat(200);
            assertEquals(400, emoji200.length(), "sanity check — emoji is 2 chars");
            assertEquals(200, emoji200.codePointCount(0, emoji200.length()));
            assertDoesNotThrow(() -> new SentenceText(emoji200));
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
        void exceeds_200_code_points_is_rejected() {
            String over200 = "a".repeat(201);
            InvalidSentenceTextException ex = assertThrows(
                InvalidSentenceTextException.class,
                () -> new SentenceText(over200)
            );
            assertTrue(ex.getMessage().contains("max 200"));
        }

        @Test
        void emoji_201_code_points_is_rejected() {
            String over200 = "😀".repeat(201);
            assertThrows(InvalidSentenceTextException.class, () -> new SentenceText(over200));
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
