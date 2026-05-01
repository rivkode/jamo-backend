package app.backend.jamo.diary.infrastructure.persistence;

import app.backend.jamo.diary.domain.model.comment.Comment;
import app.backend.jamo.diary.domain.model.comment.CommentContent;
import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.model.commentlike.CommentLike;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.infrastructure.persistence.repository.CommentLikeRepositoryImpl;
import app.backend.jamo.diary.infrastructure.persistence.repository.CommentRepositoryImpl;
import app.backend.jamo.diary.infrastructure.support.AbstractMySQLContainerTest;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CommentLikeRepositoryImpl.class, CommentRepositoryImpl.class})
@ActiveProfiles("test")
class CommentLikeRepositoryImplDataJpaTest extends AbstractMySQLContainerTest {

    @Autowired private CommentLikeRepositoryImpl repository;
    @Autowired private CommentRepositoryImpl commentRepository;
    @Autowired private EntityManager entityManager;

    private final Instant baseTime = Instant.parse("2026-05-01T00:00:00Z");
    private final Clock clock = Clock.fixed(baseTime, ZoneOffset.UTC);

    @Test
    void save_then_findByCommentIdAndUserId_round_trip() {
        CommentId commentId = CommentId.newId();
        UUID user = UUID.randomUUID();
        CommentLike like = CommentLike.create(commentId, user, clock);

        repository.save(like);
        flushAndClear();

        assertThat(repository.findByCommentIdAndUserId(commentId, user)).isPresent();
        assertThat(repository.existsByCommentIdAndUserId(commentId, user)).isTrue();
    }

    @Test
    void unique_constraint_blocks_duplicate_comment_user_pair() {
        CommentId commentId = CommentId.newId();
        UUID user = UUID.randomUUID();
        repository.save(CommentLike.create(commentId, user, clock));
        flushAndClear();

        repository.save(CommentLike.create(commentId, user, clock));

        assertThatThrownBy(this::flushAndClear).isInstanceOfAny(
            DataIntegrityViolationException.class,
            ConstraintViolationException.class);
    }

    @Test
    void deleteByCommentIdAndUserId_is_idempotent() {
        CommentId commentId = CommentId.newId();
        UUID user = UUID.randomUUID();

        repository.deleteByCommentIdAndUserId(commentId, user);
        flushAndClear();
        assertThat(repository.existsByCommentIdAndUserId(commentId, user)).isFalse();

        repository.save(CommentLike.create(commentId, user, clock));
        flushAndClear();
        repository.deleteByCommentIdAndUserId(commentId, user);
        flushAndClear();
        assertThat(repository.existsByCommentIdAndUserId(commentId, user)).isFalse();
    }

    @Test
    void findCommentIdsLikedByUser_returns_subset_and_empty_input_short_circuits() {
        UUID user = UUID.randomUUID();
        CommentId c1 = CommentId.newId();
        CommentId c2 = CommentId.newId();
        CommentId c3 = CommentId.newId();
        repository.save(CommentLike.create(c1, user, clock));
        repository.save(CommentLike.create(c3, user, clock));
        flushAndClear();

        assertThat(repository.findCommentIdsLikedByUser(user, Set.of(c1, c2, c3)))
            .containsExactlyInAnyOrder(c1, c3);
        assertThat(repository.findCommentIdsLikedByUser(user, Set.of())).isEmpty();
    }

    @Test
    void deleteAllByCommentId_and_userId_cascade() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        CommentId c1 = CommentId.newId();
        CommentId c2 = CommentId.newId();
        repository.save(CommentLike.create(c1, userA, clock));
        repository.save(CommentLike.create(c1, userB, clock));
        repository.save(CommentLike.create(c2, userA, clock));
        flushAndClear();

        assertThat(repository.deleteAllByCommentId(c1)).isEqualTo(2);
        flushAndClear();
        assertThat(repository.findCommentIdsLikedByUser(userB, Set.of(c1))).isEmpty();

        assertThat(repository.deleteAllByUserId(userA)).isEqualTo(1);
        flushAndClear();
        assertThat(repository.findCommentIdsLikedByUser(userA, Set.of(c2))).isEmpty();
    }

    @Test
    void deleteAllByCommentParentId_and_diaryId_use_comment_join() {
        UUID author = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        DiaryId diary = DiaryId.newId();
        DiaryId otherDiary = DiaryId.newId();
        Comment root = newComment(diary, author, null, "root", baseTime);
        Comment child1 = newComment(diary, author, root.id(), "child1", baseTime.plusSeconds(1));
        Comment child2 = newComment(diary, author, root.id(), "child2", baseTime.plusSeconds(2));
        Comment other = newComment(otherDiary, author, null, "other", baseTime.plusSeconds(3));
        commentRepository.save(root);
        commentRepository.save(child1);
        commentRepository.save(child2);
        commentRepository.save(other);
        repository.save(CommentLike.create(root.id(), user, clock));
        repository.save(CommentLike.create(child1.id(), user, clock));
        repository.save(CommentLike.create(child2.id(), user, clock));
        repository.save(CommentLike.create(other.id(), user, clock));
        flushAndClear();

        assertThat(repository.deleteAllByCommentParentId(root.id())).isEqualTo(2);
        flushAndClear();
        assertThat(repository.existsByCommentIdAndUserId(root.id(), user)).isTrue();
        assertThat(repository.existsByCommentIdAndUserId(child1.id(), user)).isFalse();

        assertThat(repository.deleteAllByDiaryId(diary)).isEqualTo(1);
        flushAndClear();
        assertThat(repository.existsByCommentIdAndUserId(root.id(), user)).isFalse();
        assertThat(repository.existsByCommentIdAndUserId(other.id(), user)).isTrue();
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

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
