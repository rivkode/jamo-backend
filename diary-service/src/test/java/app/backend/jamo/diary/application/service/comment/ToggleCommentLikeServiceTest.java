package app.backend.jamo.diary.application.service.comment;

import app.backend.jamo.diary.application.dto.comment.ToggleCommentLikeCommand;
import app.backend.jamo.diary.application.dto.comment.ToggleCommentLikeView;
import app.backend.jamo.diary.domain.exception.CommentNotFoundException;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.model.comment.Comment;
import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.model.commentlike.CommentLike;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.repository.CommentLikeRepository;
import app.backend.jamo.diary.domain.repository.CommentRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToggleCommentLikeServiceTest {

    private CommentRepository commentRepository;
    private CommentLikeRepository commentLikeRepository;
    private DiaryRepository diaryRepository;
    private TransactionTemplate transactionTemplate;
    private ToggleCommentLikeService service;

    @BeforeEach
    void setUp() {
        commentRepository = mock(CommentRepository.class);
        commentLikeRepository = mock(CommentLikeRepository.class);
        diaryRepository = mock(DiaryRepository.class);
        transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
            ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null)
        );
        service = new ToggleCommentLikeService(
            commentRepository, commentLikeRepository, diaryRepository, transactionTemplate,
            CommentTestFixtures.fixedClock()
        );
    }

    @Test
    void liked_true_when_not_already_liked_inserts_and_increments_counter() {
        UUID viewer = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        Comment comment = CommentTestFixtures.rootComment(diary.id(), author);

        when(commentRepository.findById(comment.id())).thenReturn(Optional.of(comment));
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(commentLikeRepository.existsByCommentIdAndUserId(comment.id(), viewer))
            .thenReturn(false);

        ToggleCommentLikeView view = service.toggle(new ToggleCommentLikeCommand(
            comment.id().value(), viewer, true
        ));

        assertThat(view.commentId()).isEqualTo(comment.id().value());
        assertThat(view.liked()).isTrue();
        assertThat(view.likeCount()).isEqualTo(1);
        verify(commentLikeRepository).save(any(CommentLike.class));
        verify(commentRepository).save(comment);
    }

    @Test
    void liked_true_when_already_liked_is_no_op() {
        UUID viewer = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        Comment comment = CommentTestFixtures.commentWithLikes(diary.id(), author, null, 3);

        when(commentRepository.findById(comment.id())).thenReturn(Optional.of(comment));
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(commentLikeRepository.existsByCommentIdAndUserId(comment.id(), viewer))
            .thenReturn(true);

        ToggleCommentLikeView view = service.toggle(new ToggleCommentLikeCommand(
            comment.id().value(), viewer, true
        ));

        assertThat(view.liked()).isTrue();
        assertThat(view.likeCount()).isEqualTo(3);
        verify(commentLikeRepository, never()).save(any());
        verify(commentRepository, never()).save(any());
    }

    @Test
    void liked_false_when_already_liked_deletes_and_decrements() {
        UUID viewer = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        Comment comment = CommentTestFixtures.commentWithLikes(diary.id(), author, null, 5);

        when(commentRepository.findById(comment.id())).thenReturn(Optional.of(comment));
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(commentLikeRepository.existsByCommentIdAndUserId(comment.id(), viewer))
            .thenReturn(true);

        ToggleCommentLikeView view = service.toggle(new ToggleCommentLikeCommand(
            comment.id().value(), viewer, false
        ));

        assertThat(view.liked()).isFalse();
        assertThat(view.likeCount()).isEqualTo(4);
        verify(commentLikeRepository).deleteByCommentIdAndUserId(comment.id(), viewer);
        verify(commentRepository).save(comment);
    }

    @Test
    void liked_false_when_not_already_liked_is_no_op() {
        UUID viewer = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        Comment comment = CommentTestFixtures.rootComment(diary.id(), author);

        when(commentRepository.findById(comment.id())).thenReturn(Optional.of(comment));
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(commentLikeRepository.existsByCommentIdAndUserId(comment.id(), viewer))
            .thenReturn(false);

        ToggleCommentLikeView view = service.toggle(new ToggleCommentLikeCommand(
            comment.id().value(), viewer, false
        ));

        assertThat(view.liked()).isFalse();
        assertThat(view.likeCount()).isZero();
        verify(commentLikeRepository, never()).deleteByCommentIdAndUserId(any(), any());
        verify(commentRepository, never()).save(any());
    }

    @Test
    void self_like_is_allowed() {
        // 박제 §8 — 자기 댓글 좋아요 허용
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        Comment comment = CommentTestFixtures.rootComment(diary.id(), author);

        when(commentRepository.findById(comment.id())).thenReturn(Optional.of(comment));
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(commentLikeRepository.existsByCommentIdAndUserId(comment.id(), author))
            .thenReturn(false);

        ToggleCommentLikeView view = service.toggle(new ToggleCommentLikeCommand(
            comment.id().value(), author, true
        ));

        assertThat(view.liked()).isTrue();
        assertThat(view.likeCount()).isEqualTo(1);
    }

    @Test
    void comment_not_found_throws_404() {
        UUID viewer = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        when(commentRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggle(new ToggleCommentLikeCommand(
            commentId, viewer, true
        ))).isInstanceOf(CommentNotFoundException.class);
    }

    @Test
    void private_diary_non_author_throws_404_idor() {
        UUID author = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        Diary privateDiary = CommentTestFixtures.privateDiary(author);
        Comment comment = CommentTestFixtures.rootComment(privateDiary.id(), author);

        when(commentRepository.findById(comment.id())).thenReturn(Optional.of(comment));
        when(diaryRepository.findById(privateDiary.id())).thenReturn(Optional.of(privateDiary));

        assertThatThrownBy(() -> service.toggle(new ToggleCommentLikeCommand(
            comment.id().value(), otherUser, true
        ))).isInstanceOf(DiaryNotFoundException.class);
    }

    @Test
    void race_window_uniques_violation_falls_back_to_actual_db_state() {
        // #81 cleanup 패턴 정합 — race 시 actualLiked DB 진실 응답
        UUID viewer = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        Comment comment = CommentTestFixtures.rootComment(diary.id(), author);

        // 첫 번째 호출 (rw): findById 후 existsBy=false → save 시 UNIQUE 위반
        // 두 번째 호출 (fallback ro): findById 재조회 + existsBy=true (다른 tx 가 이미 INSERT)
        AtomicInteger findIdCount = new AtomicInteger(0);
        when(commentRepository.findById(comment.id())).thenAnswer(inv -> {
            findIdCount.incrementAndGet();
            return Optional.of(comment);
        });
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        AtomicInteger existsCount = new AtomicInteger(0);
        when(commentLikeRepository.existsByCommentIdAndUserId(comment.id(), viewer))
            .thenAnswer(inv -> existsCount.incrementAndGet() == 1 ? false : true);
        doThrow(new DataIntegrityViolationException("uk_comment_like_comment_user"))
            .when(commentLikeRepository).save(any());

        ToggleCommentLikeView view = service.toggle(new ToggleCommentLikeCommand(
            comment.id().value(), viewer, true
        ));

        // actualLiked DB 진실 — race 후 다른 tx 가 INSERT 했으므로 true
        assertThat(view.liked()).isTrue();
        // commentRepository.findById 두 번 호출 (rw + fallback ro)
        assertThat(findIdCount.get()).isEqualTo(2);
        // transactionTemplate.execute 두 번
        verify(transactionTemplate, times(2)).execute(any());
    }

    @Test
    void race_after_delete_returns_actual_false_in_fallback() {
        // #81 cleanup H1 정합 — race 후 또 다른 tx 가 DELETE 까지 한 극단 케이스
        UUID viewer = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        Comment comment = CommentTestFixtures.rootComment(diary.id(), author);

        when(commentRepository.findById(comment.id())).thenReturn(Optional.of(comment));
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        // 첫 호출: false (insert 시도) / fallback ro: false (다른 tx 가 INSERT 후 또 DELETE)
        when(commentLikeRepository.existsByCommentIdAndUserId(comment.id(), viewer))
            .thenAnswer(inv -> false);
        doThrow(new DataIntegrityViolationException("uk_comment_like"))
            .when(commentLikeRepository).save(any());

        ToggleCommentLikeView view = service.toggle(new ToggleCommentLikeCommand(
            comment.id().value(), viewer, true
        ));

        // command.liked() = true 였지만 DB 진실 = false → echo 가 아닌 actualLiked 노출
        assertThat(view.liked()).isFalse();
    }
}
