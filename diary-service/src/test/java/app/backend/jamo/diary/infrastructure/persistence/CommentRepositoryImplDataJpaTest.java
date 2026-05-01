package app.backend.jamo.diary.infrastructure.persistence;

import app.backend.jamo.diary.domain.model.comment.Comment;
import app.backend.jamo.diary.domain.model.comment.CommentContent;
import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.repository.cursor.CommentCursor;
import app.backend.jamo.diary.infrastructure.persistence.repository.CommentRepositoryImpl;
import app.backend.jamo.diary.infrastructure.support.AbstractMySQLContainerTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CommentRepositoryImpl.class)
@ActiveProfiles("test")
class CommentRepositoryImplDataJpaTest extends AbstractMySQLContainerTest {

    @Autowired private CommentRepositoryImpl repository;
    @Autowired private EntityManager entityManager;

    private final Instant baseTime = Instant.parse("2026-05-01T00:00:00Z");

    @Test
    void save_then_findById_round_trip_root_and_reply() {
        DiaryId diaryId = DiaryId.newId();
        UUID author = UUID.randomUUID();
        Comment root = newComment(diaryId, author, null, "root", baseTime);
        Comment reply = newComment(diaryId, author, root.id(), "reply", baseTime.plusSeconds(1));

        repository.save(root);
        repository.save(reply);
        flushAndClear();

        Optional<Comment> loadedRoot = repository.findById(root.id());
        Optional<Comment> loadedReply = repository.findById(reply.id());
        assertThat(loadedRoot).isPresent();
        assertThat(loadedRoot.get().parentId()).isEmpty();
        assertThat(loadedRoot.get().content().value()).isEqualTo("root");
        assertThat(loadedReply).isPresent();
        assertThat(loadedReply.get().parentId()).contains(root.id());
        assertThat(loadedReply.get().createdAt()).isEqualTo(baseTime.plusSeconds(1));
    }

    @Test
    void save_existing_comment_updates_like_count_only() {
        Comment comment = newComment(DiaryId.newId(), UUID.randomUUID(), null, "root", baseTime);
        repository.save(comment);
        flushAndClear();

        Comment loaded = repository.findById(comment.id()).orElseThrow();
        loaded.onLikeAdded();
        loaded.onLikeAdded();
        repository.save(loaded);
        flushAndClear();

        assertThat(repository.findById(comment.id()).orElseThrow().likeCount()).isEqualTo(2);
    }

    @Test
    void findByDiaryId_orders_chronological_and_uses_cursor() {
        DiaryId target = DiaryId.newId();
        DiaryId other = DiaryId.newId();
        UUID author = UUID.randomUUID();
        Comment c1 = newComment(target, author, null, "c1", baseTime);
        Comment c2 = newComment(target, author, null, "c2", baseTime.plusSeconds(1));
        Comment c3 = newComment(target, author, null, "c3", baseTime.plusSeconds(2));
        Comment otherComment = newComment(other, author, null, "other", baseTime.plusSeconds(3));
        repository.save(c2);
        repository.save(otherComment);
        repository.save(c1);
        repository.save(c3);
        flushAndClear();

        List<Comment> firstPage = repository.findByDiaryId(target, null, 2);
        assertThat(firstPage).extracting(Comment::id).containsExactly(c1.id(), c2.id());

        CommentCursor cursor = new CommentCursor(c2.createdAt(), c2.id());
        List<Comment> secondPage = repository.findByDiaryId(target, cursor, 2);
        assertThat(secondPage).extracting(Comment::id).containsExactly(c3.id());
    }

    @Test
    void findByDiaryId_keyset_tiebreaks_by_comment_id_when_created_at_equal() {
        DiaryId diaryId = DiaryId.newId();
        UUID author = UUID.randomUUID();
        CommentId idA = CommentId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        CommentId idB = CommentId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        CommentId idC = CommentId.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        repository.save(newComment(idA, diaryId, author, null, "a", baseTime));
        repository.save(newComment(idB, diaryId, author, null, "b", baseTime));
        repository.save(newComment(idC, diaryId, author, null, "c", baseTime));
        flushAndClear();

        List<Comment> firstPage = repository.findByDiaryId(diaryId, null, 2);
        assertThat(firstPage).extracting(Comment::id).containsExactly(idA, idB);

        CommentCursor cursor = new CommentCursor(baseTime, idB);
        List<Comment> secondPage = repository.findByDiaryId(diaryId, cursor, 2);
        assertThat(secondPage).extracting(Comment::id).containsExactly(idC);
    }

    @Test
    void deleteById_is_idempotent() {
        CommentId missing = CommentId.newId();
        repository.deleteById(missing);
        flushAndClear();

        Comment comment = newComment(DiaryId.newId(), UUID.randomUUID(), null, "root", baseTime);
        repository.save(comment);
        flushAndClear();

        repository.deleteById(comment.id());
        flushAndClear();

        assertThat(repository.findById(comment.id())).isEmpty();
    }

    @Test
    void deleteAllByParentId_and_diary_and_author_cascade() {
        UUID author = UUID.randomUUID();
        UUID otherAuthor = UUID.randomUUID();
        DiaryId diary = DiaryId.newId();
        DiaryId otherDiary = DiaryId.newId();
        Comment root = newComment(diary, author, null, "root", baseTime);
        Comment child1 = newComment(diary, author, root.id(), "child1", baseTime.plusSeconds(1));
        Comment child2 = newComment(diary, author, root.id(), "child2", baseTime.plusSeconds(2));
        Comment other = newComment(otherDiary, otherAuthor, null, "other", baseTime.plusSeconds(3));
        repository.save(root);
        repository.save(child1);
        repository.save(child2);
        repository.save(other);
        flushAndClear();

        assertThat(repository.deleteAllByParentId(root.id())).isEqualTo(2);
        flushAndClear();
        assertThat(repository.findById(root.id())).isPresent();
        assertThat(repository.findById(child1.id())).isEmpty();

        assertThat(repository.deleteAllByAuthorId(otherAuthor)).isEqualTo(1);
        flushAndClear();
        assertThat(repository.findById(other.id())).isEmpty();

        assertThat(repository.deleteAllByDiaryId(diary)).isEqualTo(1);
        flushAndClear();
        assertThat(repository.findById(root.id())).isEmpty();
    }

    @Test
    void findChildrenByParentIdForUpdate_returns_only_direct_children() {
        UUID author = UUID.randomUUID();
        DiaryId diary = DiaryId.newId();
        Comment root = newComment(diary, author, null, "root", baseTime);
        Comment child1 = newComment(diary, author, root.id(), "child1", baseTime.plusSeconds(1));
        Comment child2 = newComment(diary, author, root.id(), "child2", baseTime.plusSeconds(2));
        Comment otherRoot = newComment(diary, author, null, "other", baseTime.plusSeconds(3));
        repository.save(root);
        repository.save(child1);
        repository.save(child2);
        repository.save(otherRoot);
        flushAndClear();

        List<Comment> children = repository.findChildrenByParentIdForUpdate(root.id());

        assertThat(children).extracting(Comment::id).containsExactlyInAnyOrder(child1.id(), child2.id());
    }

    private static Comment newComment(
        DiaryId diaryId,
        UUID authorId,
        CommentId parentIdOrNull,
        String content,
        Instant createdAt
    ) {
        return Comment.reconstitute(
            CommentId.newId(), diaryId, authorId, new CommentContent(content),
            parentIdOrNull, 0, createdAt);
    }

    private static Comment newComment(
        CommentId commentId,
        DiaryId diaryId,
        UUID authorId,
        CommentId parentIdOrNull,
        String content,
        Instant createdAt
    ) {
        return Comment.reconstitute(
            commentId, diaryId, authorId, new CommentContent(content),
            parentIdOrNull, 0, createdAt);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
