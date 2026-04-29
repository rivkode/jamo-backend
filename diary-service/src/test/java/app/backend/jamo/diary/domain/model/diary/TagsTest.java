package app.backend.jamo.diary.domain.model.diary;

import app.backend.jamo.diary.domain.exception.InvalidTagException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagsTest {

    @Test
    void empty_constructor_is_allowed() {
        Tags tags = Tags.empty();
        assertTrue(tags.isEmpty());
        assertEquals(0, tags.size());
    }

    @Test
    void accepts_within_bounds() {
        Tags tags = Tags.ofStrings(List.of("일상", "감정", "운동"));
        assertAll(
            () -> assertEquals(3, tags.size()),
            () -> assertEquals(List.of("일상", "감정", "운동"), tags.asStrings())
        );
    }

    @Test
    void rejects_null_list() {
        assertThrows(NullPointerException.class, () -> new Tags(null));
    }

    @Test
    void rejects_over_max_size() {
        List<String> over = java.util.stream.IntStream.range(0, Tags.MAX_SIZE + 1)
            .mapToObj(i -> "tag" + i)
            .toList();
        assertThrows(InvalidTagException.class, () -> Tags.ofStrings(over));
    }

    @Test
    void accepts_at_max_size_boundary() {
        List<String> boundary = java.util.stream.IntStream.range(0, Tags.MAX_SIZE)
            .mapToObj(i -> "tag" + i)
            .toList();
        Tags tags = Tags.ofStrings(boundary);
        assertEquals(Tags.MAX_SIZE, tags.size());
    }

    @Test
    void rejects_duplicate() {
        assertThrows(InvalidTagException.class,
            () -> Tags.ofStrings(List.of("일상", "일상")));
    }

    @Test
    void propagates_individual_tag_invariant() {
        // 길이 초과 단일 tag
        String overLong = "a".repeat(Tag.MAX_CODE_POINTS + 1);
        assertThrows(InvalidTagException.class,
            () -> Tags.ofStrings(List.of("ok", overLong)));
    }

    @Test
    void list_is_immutable_copy() {
        java.util.List<String> mutable = new java.util.ArrayList<>(List.of("a", "b"));
        Tags tags = Tags.ofStrings(mutable);
        mutable.clear();
        assertEquals(2, tags.size(), "원본 변경이 Tags 에 영향 X");
    }

    @Test
    void treats_case_difference_as_distinct() {
        // 박제 §6 — 정규화 미적용
        Tags tags = Tags.ofStrings(List.of("abc", "ABC"));
        assertEquals(2, tags.size());
    }

    @Test
    void rejects_null_element_in_ofStrings() {
        java.util.List<String> withNull = new java.util.ArrayList<>();
        withNull.add("ok");
        withNull.add(null);
        InvalidTagException ex = assertThrows(InvalidTagException.class,
            () -> Tags.ofStrings(withNull));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("index 1"));
    }
}
