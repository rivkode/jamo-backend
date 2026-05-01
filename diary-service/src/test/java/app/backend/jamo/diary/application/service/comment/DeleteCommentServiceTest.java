package app.backend.jamo.diary.application.service.comment;

import app.backend.jamo.diary.application.dto.comment.DeleteCommentCommand;
import app.backend.jamo.diary.domain.exception.CommentNotFoundException;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.model.comment.Comment;
import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.repository.CommentLikeRepository;
import app.backend.jamo.diary.domain.repository.CommentRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeleteCommentServiceTest {

    private CommentRepository commentRepository;
    private CommentLikeRepository commentLikeRepository;
    private DiaryRepository diaryRepository;
    private DeleteCommentService service;

    @BeforeEach
    void setUp() {
        commentRepository = mock(CommentRepository.class);
        commentLikeRepository = mock(CommentLikeRepository.class);
        diaryRepository = mock(DiaryRepository.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        // DeleteCommentService 는 executeWithoutResult(Consumer<TransactionStatus>) 만 사용
        doAnswer(inv -> {
            Consumer<TransactionStatus> action = inv.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        service = new DeleteCommentService(
            commentRepository, commentLikeRepository, diaryRepository, transactionTemplate
        );
    }

    @Test
    void root_comment_with_replies_cascades_likes_and_decrements_diary_counter() {
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        // Diary 가 댓글 + 답글 5건 있다고 가정
        for (int i = 0; i < 5; i++) {
            diary.onCommentAdded();
        }
        assertThat(diary.commentCount()).isEqualTo(5);
        Comment root = CommentTestFixtures.rootComment(diary.id(), author);
        Comment child1 = CommentTestFixtures.reply(diary.id(), author, root.id());
        Comment child2 = CommentTestFixtures.reply(diary.id(), author, root.id());
        Comment child3 = CommentTestFixtures.reply(diary.id(), author, root.id());
        Comment child4 = CommentTestFixtures.reply(diary.id(), author, root.id());

        when(commentRepository.findByIdWithoutLock(root.id())).thenReturn(Optional.of(root));
        when(commentRepository.findById(root.id())).thenReturn(Optional.of(root));
        when(diaryRepository.findByIdForUpdate(diary.id())).thenReturn(Optional.of(diary));
        when(commentRepository.findChildrenByParentIdForUpdate(root.id()))
            .thenReturn(List.of(child1, child2, child3, child4));

        service.delete(new DeleteCommentCommand(root.id().value(), author));

        // cascade 순서: 자식 댓글 row lock → 자식 좋아요 → 자식 댓글 → 본인 좋아요 → 본인 댓글 → Diary save
        InOrder order = inOrder(commentLikeRepository, commentRepository, diaryRepository);
        order.verify(commentRepository).findByIdWithoutLock(root.id());
        order.verify(diaryRepository).findByIdForUpdate(diary.id());
        order.verify(commentRepository).findById(root.id());
        order.verify(commentRepository).findChildrenByParentIdForUpdate(root.id());
        order.verify(commentLikeRepository).deleteAllByCommentParentId(root.id());
        order.verify(commentRepository).deleteAllByParentId(root.id());
        order.verify(commentLikeRepository).deleteAllByCommentId(root.id());
        order.verify(commentRepository).deleteById(root.id());
        order.verify(diaryRepository).save(diary);

        // Diary.commentCount = 5 - (1 본인 + 4 자식) = 0
        assertThat(diary.commentCount()).isZero();
    }

    @Test
    void leaf_reply_does_not_call_parent_cascade() {
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        diary.onCommentAdded(); // 답글 1건만 있다고 가정
        assertThat(diary.commentCount()).isEqualTo(1);
        Comment root = CommentTestFixtures.rootComment(diary.id(), author);
        Comment reply = CommentTestFixtures.reply(diary.id(), author, root.id());

        when(commentRepository.findByIdWithoutLock(reply.id())).thenReturn(Optional.of(reply));
        when(commentRepository.findById(reply.id())).thenReturn(Optional.of(reply));
        when(diaryRepository.findByIdForUpdate(diary.id())).thenReturn(Optional.of(diary));

        service.delete(new DeleteCommentCommand(reply.id().value(), author));

        // 답글 cascade 호출 없음 (자식 없음)
        verify(commentRepository, never()).findChildrenByParentIdForUpdate(any());
        verify(commentLikeRepository, never()).deleteAllByCommentParentId(any());
        verify(commentRepository, never()).deleteAllByParentId(any());
        // 본인 좋아요 + 본인 댓글만 삭제
        verify(commentLikeRepository).deleteAllByCommentId(reply.id());
        verify(commentRepository).deleteById(reply.id());
        // Diary.commentCount = 1 - 1 = 0
        assertThat(diary.commentCount()).isZero();
        verify(diaryRepository).save(diary);
    }

    @Test
    void comment_not_found_throws_404() {
        UUID requester = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        when(commentRepository.findByIdWithoutLock(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(new DeleteCommentCommand(commentId, requester)))
            .isInstanceOf(CommentNotFoundException.class);

        verify(commentRepository, never()).deleteById(any());
    }

    @Test
    void non_author_throws_404_idor() {
        UUID author = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        Comment comment = CommentTestFixtures.rootComment(diary.id(), author);
        when(commentRepository.findByIdWithoutLock(comment.id())).thenReturn(Optional.of(comment));
        when(commentRepository.findById(comment.id())).thenReturn(Optional.of(comment));
        when(diaryRepository.findByIdForUpdate(diary.id())).thenReturn(Optional.of(diary));

        assertThatThrownBy(() -> service.delete(new DeleteCommentCommand(
            comment.id().value(), otherUser
        )))
            .isInstanceOf(CommentNotFoundException.class)
            .hasMessageContaining("comment not found");

        verify(commentRepository, never()).deleteById(any());
        verify(commentLikeRepository, never()).deleteAllByCommentId(any());
    }

    @Test
    void diary_not_found_after_comment_load_throws_404() {
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        Comment comment = CommentTestFixtures.rootComment(diary.id(), author);
        when(commentRepository.findByIdWithoutLock(comment.id())).thenReturn(Optional.of(comment));
        when(diaryRepository.findByIdForUpdate(diary.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(new DeleteCommentCommand(
            comment.id().value(), author
        ))).isInstanceOf(DiaryNotFoundException.class);

        verify(commentRepository, never()).deleteById(any());
    }

    @Test
    void private_diary_non_owner_throws_404_idor() {
        // 작성자 본인의 댓글이라도 일기 visibility 가 변경된 케이스
        UUID author = UUID.randomUUID();
        UUID otherDiaryAuthor = UUID.randomUUID();
        Diary privateDiary = CommentTestFixtures.privateDiary(otherDiaryAuthor);
        Comment commentByAuthor = Comment.create(
            CommentId.newId(), privateDiary.id(), author,
            new app.backend.jamo.diary.domain.model.comment.CommentContent("내 댓글"),
            null, CommentTestFixtures.fixedClock()
        );
        when(commentRepository.findByIdWithoutLock(commentByAuthor.id())).thenReturn(Optional.of(commentByAuthor));
        when(commentRepository.findById(commentByAuthor.id())).thenReturn(Optional.of(commentByAuthor));
        when(diaryRepository.findByIdForUpdate(privateDiary.id())).thenReturn(Optional.of(privateDiary));

        assertThatThrownBy(() -> service.delete(new DeleteCommentCommand(
            commentByAuthor.id().value(), author
        ))).isInstanceOf(DiaryNotFoundException.class);

        verify(commentRepository, never()).deleteById(any());
    }
}
