package app.backend.jamo.diary.domain.model.comment;

import app.backend.jamo.diary.domain.exception.InvalidCommentContentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentContentTest {

    @Test
    void accepts_within_bounds() {
        CommentContent c = new CommentContent("좋은 글이네요!");
        assertEquals("좋은 글이네요!", c.value());
    }

    @Test
    void rejects_null() {
        assertThrows(NullPointerException.class, () -> new CommentContent(null));
    }

    @Test
    void rejects_empty() {
        InvalidCommentContentException ex = assertThrows(InvalidCommentContentException.class,
            () -> new CommentContent(""));
        assertTrue(ex.getMessage().contains("blank"));
    }

    @Test
    void rejects_whitespace_only() {
        assertThrows(InvalidCommentContentException.class, () -> new CommentContent("   \n\t "));
    }

    @Test
    void counts_emoji_as_single_code_point() {
        // 😊 = 1 code point (surrogate pair 2 char) — invariant 통과
        CommentContent c = new CommentContent("😊");
        assertEquals("😊", c.value());
    }

    @Test
    void emoji_at_max_code_points_boundary_is_accepted() {
        String boundary = "😊".repeat(CommentContent.MAX_CODE_POINTS);
        // sanity — surrogate pair 라 char length 는 2x
        assertEquals(2 * CommentContent.MAX_CODE_POINTS, boundary.length());
        CommentContent c = new CommentContent(boundary);
        assertEquals(boundary, c.value());
    }

    @Test
    void emoji_over_max_code_points_is_rejected() {
        String over = "😊".repeat(CommentContent.MAX_CODE_POINTS + 1);
        InvalidCommentContentException ex = assertThrows(InvalidCommentContentException.class,
            () -> new CommentContent(over));
        assertTrue(ex.getMessage().contains("max " + CommentContent.MAX_CODE_POINTS));
    }

    @Test
    void rejects_over_max_code_points_ascii() {
        String over = "a".repeat(CommentContent.MAX_CODE_POINTS + 1);
        InvalidCommentContentException ex = assertThrows(InvalidCommentContentException.class,
            () -> new CommentContent(over));
        assertTrue(ex.getMessage().contains("got " + (CommentContent.MAX_CODE_POINTS + 1)));
    }

    @Test
    void accepts_at_max_code_points_boundary_korean() {
        String boundary = "가".repeat(CommentContent.MAX_CODE_POINTS);
        CommentContent c = new CommentContent(boundary);
        assertEquals(boundary, c.value());
    }

    @Test
    void max_is_500_code_points() {
        // 사용자 결정 — diary 본문 2000cp 의 1/4
        assertEquals(500, CommentContent.MAX_CODE_POINTS);
    }
}
