package app.backend.jamo.diary.domain.model.diary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiaryTest {

    private DiaryId id;
    private UUID author;
    private UUID otherUser;
    private DiaryLines lines;
    private Tags tags;
    private ImageUrls images;
    private Instant now;
    private Clock clock;

    @BeforeEach
    void setUp() {
        id = DiaryId.newId();
        author = UUID.randomUUID();
        otherUser = UUID.randomUUID();
        lines = new DiaryLines(List.of("오늘 산책", "날씨 좋다", "기분 좋음"));
        tags = Tags.ofStrings(List.of("일상"));
        images = ImageUrls.empty();
        now = Instant.parse("2026-04-29T10:00:00Z");
        clock = Clock.fixed(now, ZoneOffset.UTC);
    }

    @Nested
    class Create {

        @Test
        void initializes_with_zero_counters() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);
            assertAll(
                () -> assertEquals(id, d.id()),
                () -> assertEquals(author, d.authorId()),
                () -> assertEquals(lines, d.lines()),
                () -> assertEquals(images, d.images()),
                () -> assertEquals(tags, d.tags()),
                () -> assertEquals(Visibility.PUBLIC, d.visibility()),
                () -> assertEquals(0, d.likeCount()),
                () -> assertEquals(0, d.commentCount()),
                () -> assertEquals(now, d.createdAt())
            );
        }

        @Test
        void rejects_null_required_fields() {
            assertThrows(NullPointerException.class,
                () -> Diary.create(null, author, lines, images, tags, Visibility.PUBLIC, clock));
            assertThrows(NullPointerException.class,
                () -> Diary.create(id, null, lines, images, tags, Visibility.PUBLIC, clock));
            assertThrows(NullPointerException.class,
                () -> Diary.create(id, author, null, images, tags, Visibility.PUBLIC, clock));
            assertThrows(NullPointerException.class,
                () -> Diary.create(id, author, lines, null, tags, Visibility.PUBLIC, clock));
            assertThrows(NullPointerException.class,
                () -> Diary.create(id, author, lines, images, null, Visibility.PUBLIC, clock));
            assertThrows(NullPointerException.class,
                () -> Diary.create(id, author, lines, images, tags, null, clock));
            assertThrows(NullPointerException.class,
                () -> Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, null));
        }
    }

    @Nested
    class Reconstitute {

        @Test
        void preserves_existing_counters() {
            Diary d = Diary.reconstitute(id, author, lines, images, tags, Visibility.PRIVATE, 7, 3, now);
            assertAll(
                () -> assertEquals(7, d.likeCount()),
                () -> assertEquals(3, d.commentCount()),
                () -> assertEquals(Visibility.PRIVATE, d.visibility())
            );
        }

        @Test
        void rejects_negative_counters() {
            assertThrows(IllegalArgumentException.class,
                () -> Diary.reconstitute(id, author, lines, images, tags, Visibility.PUBLIC, -1, 0, now));
            assertThrows(IllegalArgumentException.class,
                () -> Diary.reconstitute(id, author, lines, images, tags, Visibility.PUBLIC, 0, -1, now));
        }
    }

    @Nested
    class LikeCounter {

        @Test
        void onLikeAdded_then_onLikeRemoved() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);
            d.onLikeAdded();
            d.onLikeAdded();
            assertEquals(2, d.likeCount());
            d.onLikeRemoved();
            assertEquals(1, d.likeCount());
        }

        @Test
        void onLikeRemoved_below_zero_throws() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);
            assertThrows(IllegalStateException.class, d::onLikeRemoved);
        }

        @Test
        void onLikeRemoved_at_zero_throws() {
            // CommentCounter 와 패턴 통일 — 카운터가 0 인 시점에 다시 호출 시 invariant 위반
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);
            d.onLikeAdded();
            d.onLikeRemoved();
            assertEquals(0, d.likeCount());
            assertThrows(IllegalStateException.class, d::onLikeRemoved);
        }
    }

    @Nested
    class CommentCounter {

        @Test
        void onCommentAdded_then_onCommentRemoved() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);
            d.onCommentAdded();
            d.onCommentAdded();
            d.onCommentAdded();
            assertEquals(3, d.commentCount());
            d.onCommentRemoved();
            assertEquals(2, d.commentCount());
        }

        @Test
        void onCommentRemoved_below_zero_throws() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);
            assertThrows(IllegalStateException.class, d::onCommentRemoved);
        }

        @Test
        void onCommentRemoved_at_zero_throws() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);
            d.onCommentAdded();
            d.onCommentRemoved();
            assertEquals(0, d.commentCount());
            assertThrows(IllegalStateException.class, d::onCommentRemoved);
        }
    }

    @Nested
    class IsAccessibleBy {

        @Test
        void public_is_accessible_by_anyone() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);
            assertAll(
                () -> assertTrue(d.isAccessibleBy(author)),
                () -> assertTrue(d.isAccessibleBy(otherUser))
            );
        }

        @Test
        void private_grants_access_to_author() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PRIVATE, clock);
            assertTrue(d.isAccessibleBy(author));
        }

        @Test
        void private_denies_access_to_others() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PRIVATE, clock);
            assertFalse(d.isAccessibleBy(otherUser));
        }

        @Test
        void rejects_null_user() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);
            assertThrows(NullPointerException.class, () -> d.isAccessibleBy(null));
        }
    }

    @Nested
    class IsOwnedBy {

        @Test
        void true_only_for_author() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);
            assertTrue(d.isOwnedBy(author));
            assertFalse(d.isOwnedBy(otherUser));
        }

        @Test
        void public_does_not_grant_ownership() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);
            assertFalse(d.isOwnedBy(otherUser));
        }
    }

    @Nested
    class Update {

        @Test
        void replaces_content_images_tags_visibility_when_called_by_author() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);
            // 카운터 변화 — 카운터 보존 검증용
            d.onLikeAdded();
            d.onCommentAdded();
            d.onLikeAdded();

            DiaryLines newLines = new DiaryLines(List.of("수정된 본문", "수정된 본문-2", "수정된 본문-3"));
            ImageUrls newImages = new ImageUrls(List.of("https://e.io/1.png"));
            Tags newTags = Tags.ofStrings(List.of("새태그"));

            d.update(newLines, newImages, newTags, Visibility.PRIVATE, author);

            assertAll(
                () -> assertEquals(newLines, d.lines()),
                () -> assertEquals(newImages, d.images()),
                () -> assertEquals(newTags, d.tags()),
                () -> assertEquals(Visibility.PRIVATE, d.visibility()),
                // 카운터 / id / authorId / createdAt 보존
                () -> assertEquals(2, d.likeCount()),
                () -> assertEquals(1, d.commentCount()),
                () -> assertEquals(id, d.id()),
                () -> assertEquals(author, d.authorId()),
                () -> assertEquals(now, d.createdAt())
            );
        }

        @Test
        void throws_DiaryAccessDeniedException_when_editor_is_not_author() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);

            assertThrows(app.backend.jamo.diary.domain.exception.DiaryAccessDeniedException.class,
                () -> d.update(new DiaryLines(List.of("x", "x-2", "x-3")), ImageUrls.empty(), Tags.empty(),
                    Visibility.PRIVATE, otherUser));
        }

        @Test
        void throws_NullPointerException_when_any_argument_is_null() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);

            assertAll(
                () -> assertThrows(NullPointerException.class, () ->
                    d.update(null, ImageUrls.empty(), Tags.empty(), Visibility.PUBLIC, author)),
                () -> assertThrows(NullPointerException.class, () ->
                    d.update(lines, null, Tags.empty(), Visibility.PUBLIC, author)),
                () -> assertThrows(NullPointerException.class, () ->
                    d.update(lines, ImageUrls.empty(), null, Visibility.PUBLIC, author)),
                () -> assertThrows(NullPointerException.class, () ->
                    d.update(lines, ImageUrls.empty(), Tags.empty(), null, author)),
                () -> assertThrows(NullPointerException.class, () ->
                    d.update(lines, ImageUrls.empty(), Tags.empty(), Visibility.PUBLIC, null))
            );
        }

        @Test
        void update_failure_leaves_state_unchanged_when_unauthorized() {
            // 비작성자 시도 후에도 원래 lines / visibility 가 보존되어야 함 (invariant 안전성).
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);

            try {
                d.update(new DiaryLines(List.of("hacked", "hacked-2", "hacked-3")), ImageUrls.empty(), Tags.empty(),
                    Visibility.PRIVATE, otherUser);
            } catch (app.backend.jamo.diary.domain.exception.DiaryAccessDeniedException ignored) {
                // expected
            }

            assertAll(
                () -> assertEquals(lines, d.lines()),
                () -> assertEquals(Visibility.PUBLIC, d.visibility())
            );
        }

        @Test
        void update_failure_leaves_state_unchanged_when_unauthorized_AND_null_content() {
            // code-reviewer M3 — 두 invariant 가 동시 위반 시에도 state 보존. ownership 검증이 먼저 실행되어
            // DiaryAccessDeniedException 이 던져지고, null lines 까지 도달하지 않음 (그러나 둘 다 위반 케이스
            // 회귀 신호로 함께 단정).
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);

            // ownership 이 먼저 검증되므로 AccessDenied 가 던져진다 (NPE 아님).
            assertThrows(app.backend.jamo.diary.domain.exception.DiaryAccessDeniedException.class,
                () -> d.update(null, ImageUrls.empty(), Tags.empty(), Visibility.PRIVATE, otherUser));

            assertAll(
                () -> assertEquals(lines, d.lines()),
                () -> assertEquals(Visibility.PUBLIC, d.visibility())
            );
        }

        @Test
        void update_failure_leaves_state_unchanged_when_authorized_AND_null_VO() {
            // 작성자가 null lines 보낼 시 NPE 가 던져진 후에도 state 보존.
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);

            assertThrows(NullPointerException.class,
                () -> d.update(null, ImageUrls.empty(), Tags.empty(), Visibility.PRIVATE, author));

            assertAll(
                () -> assertEquals(lines, d.lines()),
                () -> assertEquals(Visibility.PUBLIC, d.visibility())
            );
        }
    }

    @Nested
    class Equality {

        @Test
        void equals_by_id_only() {
            Diary d1 = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);
            Diary d2 = Diary.reconstitute(id, author, lines, images, tags, Visibility.PRIVATE, 99, 9, now);
            assertAll(
                () -> assertEquals(d1, d2),
                () -> assertEquals(d2, d1, "symmetric"),
                () -> assertEquals(d1.hashCode(), d2.hashCode())
            );
        }

        @Test
        void equals_is_reflexive() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);
            assertEquals(d, d);
        }

        @Test
        void not_equals_different_id() {
            Diary d1 = Diary.create(DiaryId.newId(), author, lines, images, tags, Visibility.PUBLIC, clock);
            Diary d2 = Diary.create(DiaryId.newId(), author, lines, images, tags, Visibility.PUBLIC, clock);
            assertFalse(d1.equals(d2));
        }

        @Test
        void not_equals_null() {
            Diary d = Diary.create(id, author, lines, images, tags, Visibility.PUBLIC, clock);
            assertFalse(d.equals(null));
        }
    }
}
