package app.backend.jamo.diary.domain.model.sentencefeedback;

import app.backend.jamo.diary.domain.exception.InvalidSuggestionException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionTest {

    @Nested
    class HappyPath {

        @Test
        void valid_construction_assigns_all_fields() {
            SuggestionId id = SuggestionId.newId();
            Suggestion s = new Suggestion(id, "더 좋은 표현", "맥락이 자연스러움", 0.85);
            assertAll(
                () -> assertEquals(id, s.id()),
                () -> assertEquals("더 좋은 표현", s.text()),
                () -> assertEquals("맥락이 자연스러움", s.reason()),
                () -> assertEquals(0.85, s.confidence())
            );
        }

        @Test
        void confidence_zero_and_one_are_valid_boundaries() {
            assertDoesNotThrow(() -> new Suggestion(SuggestionId.newId(), "t", "r", 0.0));
            assertDoesNotThrow(() -> new Suggestion(SuggestionId.newId(), "t", "r", 1.0));
        }
    }

    @Nested
    class IdValidation {

        @ParameterizedTest
        @NullSource
        void null_id_is_rejected(SuggestionId invalid) {
            assertThrows(NullPointerException.class,
                () -> new Suggestion(invalid, "t", "r", 0.5));
        }
    }

    @Nested
    class TextValidation {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", "\t\n"})
        void blank_text_is_rejected(String invalid) {
            assertThrows(InvalidSuggestionException.class,
                () -> new Suggestion(SuggestionId.newId(), invalid, "r", 0.5));
        }

        @Test
        void text_exceeds_200_code_points_is_rejected() {
            String over200 = "a".repeat(201);
            InvalidSuggestionException ex = assertThrows(InvalidSuggestionException.class,
                () -> new Suggestion(SuggestionId.newId(), over200, "r", 0.5));
            assertTrue(ex.getMessage().contains("text"));
            assertTrue(ex.getMessage().contains("max 200"));
        }

        @Test
        void exactly_200_code_points_text_is_accepted() {
            String exact200 = "a".repeat(200);
            assertDoesNotThrow(
                () -> new Suggestion(SuggestionId.newId(), exact200, "r", 0.5));
        }
    }

    @Nested
    class ReasonValidation {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", "\t\n"})
        void blank_reason_is_rejected(String invalid) {
            // ddd-architect Q2 NEEDS CHANGES — AI invariant 위반 차단
            assertThrows(InvalidSuggestionException.class,
                () -> new Suggestion(SuggestionId.newId(), "t", invalid, 0.5));
        }

        @Test
        void reason_exceeds_500_code_points_is_rejected() {
            String over500 = "r".repeat(501);
            InvalidSuggestionException ex = assertThrows(InvalidSuggestionException.class,
                () -> new Suggestion(SuggestionId.newId(), "t", over500, 0.5));
            assertTrue(ex.getMessage().contains("reason"));
            assertTrue(ex.getMessage().contains("max 500"));
        }
    }

    @Nested
    class ConfidenceValidation {

        @Test
        void confidence_below_zero_is_rejected() {
            InvalidSuggestionException ex = assertThrows(InvalidSuggestionException.class,
                () -> new Suggestion(SuggestionId.newId(), "t", "r", -0.01));
            assertTrue(ex.getMessage().contains("confidence"));
        }

        @Test
        void confidence_above_one_is_rejected() {
            assertThrows(InvalidSuggestionException.class,
                () -> new Suggestion(SuggestionId.newId(), "t", "r", 1.01));
        }

        @Test
        void confidence_NaN_is_rejected() {
            assertThrows(InvalidSuggestionException.class,
                () -> new Suggestion(SuggestionId.newId(), "t", "r", Double.NaN));
        }

        @Test
        void confidence_positive_infinity_is_rejected() {
            assertThrows(InvalidSuggestionException.class,
                () -> new Suggestion(SuggestionId.newId(), "t", "r", Double.POSITIVE_INFINITY));
        }

        @Test
        void confidence_negative_infinity_is_rejected() {
            assertThrows(InvalidSuggestionException.class,
                () -> new Suggestion(SuggestionId.newId(), "t", "r", Double.NEGATIVE_INFINITY));
        }
    }
}
