package app.backend.jamo.diary.application.service.comment;

import app.backend.jamo.contracts.event.diary.CommentCreated;
import app.backend.jamo.diary.application.dto.comment.CommentView;
import app.backend.jamo.diary.application.dto.comment.CreateCommentCommand;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.exception.CommentNotFoundException;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.exception.InvalidCommentContentException;
import app.backend.jamo.diary.domain.exception.InvalidCommentParentException;
import app.backend.jamo.diary.domain.model.comment.Comment;
import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.repository.CommentRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import app.backend.jamo.diary.domain.repository.OutboxEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateCommentServiceTest {

    private CommentRepository commentRepository;
    private DiaryRepository diaryRepository;
    private OutboxEventPublisher outboxEventPublisher;
    private UserSummaryPort userSummaryPort;
    private CreateCommentService service;

    @BeforeEach
    void setUp() {
        commentRepository = mock(CommentRepository.class);
        diaryRepository = mock(DiaryRepository.class);
        outboxEventPublisher = mock(OutboxEventPublisher.class);
        userSummaryPort = mock(UserSummaryPort.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
            ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null)
        );
        service = new CreateCommentService(
            commentRepository, diaryRepository, outboxEventPublisher, userSummaryPort,
            transactionTemplate, CommentTestFixtures.fixedClock()
        );
    }

    @Test
    void happy_path_root_comment_persists_outbox_increments_diary_counter() {
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        when(diaryRepository.findByIdForUpdate(diary.id())).thenReturn(Optional.of(diary));
        when(userSummaryPort.get(author))
            .thenReturn(Optional.of(new UserSummaryView(author, "홍길동")));

        AtomicReference<Comment> savedComment = new AtomicReference<>();
        doAnswer(inv -> {
            savedComment.set(inv.getArgument(0));
            return null;
        }).when(commentRepository).save(any());

        CommentView view = service.create(new CreateCommentCommand(
            diary.id().value(), author, "좋은 글이네요!", null
        ));

        assertThat(view.diaryId()).isEqualTo(diary.id().value());
        assertThat(view.authorId()).isEqualTo(author);
        assertThat(view.authorDisplayName()).isEqualTo("홍길동");
        assertThat(view.content()).isEqualTo("좋은 글이네요!");
        assertThat(view.parentId()).isNull();
        assertThat(view.likeCount()).isZero();
        assertThat(view.likedByMe()).isFalse();
        assertThat(view.createdAt()).isEqualTo(CommentTestFixtures.NOW);

        // 저장된 Comment + Diary 카운터 증가 + Outbox 발행 검증
        verify(commentRepository).save(any());
        assertThat(savedComment.get().isRoot()).isTrue();
        assertThat(diary.commentCount()).isEqualTo(1);
        verify(diaryRepository).save(diary);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisher).publish(eventCaptor.capture());
        CommentCreated event = (CommentCreated) eventCaptor.getValue();
        assertThat(event.commentId()).isEqualTo(view.commentId().toString());
        assertThat(event.diaryId()).isEqualTo(diary.id().asString());
        assertThat(event.userId()).isEqualTo(author.toString());
        assertThat(event.occurredAt()).isEqualTo(CommentTestFixtures.NOW);
        assertThat(UUID.fromString(event.eventId())).isNotNull();
    }

    @Test
    void happy_path_reply_persists_with_parent_id() {
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        Comment parent = CommentTestFixtures.rootComment(diary.id(), author);
        when(diaryRepository.findByIdForUpdate(diary.id())).thenReturn(Optional.of(diary));
        when(commentRepository.findById(parent.id())).thenReturn(Optional.of(parent));
        when(userSummaryPort.get(author))
            .thenReturn(Optional.of(new UserSummaryView(author, "홍길동")));

        CommentView view = service.create(new CreateCommentCommand(
            diary.id().value(), author, "답글입니다", parent.id().value()
        ));

        assertThat(view.parentId()).isEqualTo(parent.id().value());
        verify(commentRepository).save(any());
    }

    @Test
    void diary_not_found_throws_404_no_persist() {
        UUID author = UUID.randomUUID();
        UUID diaryId = UUID.randomUUID();
        when(diaryRepository.findByIdForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateCommentCommand(
            diaryId, author, "ok", null
        ))).isInstanceOf(DiaryNotFoundException.class);

        verify(commentRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any());
    }

    @Test
    void private_diary_non_author_throws_404_idor() {
        UUID author = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        Diary privateDiary = CommentTestFixtures.privateDiary(author);
        when(diaryRepository.findByIdForUpdate(privateDiary.id())).thenReturn(Optional.of(privateDiary));

        assertThatThrownBy(() -> service.create(new CreateCommentCommand(
            privateDiary.id().value(), otherUser, "댓글", null
        ))).isInstanceOf(DiaryNotFoundException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void parent_not_found_throws_404() {
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        UUID missingParent = UUID.randomUUID();
        when(diaryRepository.findByIdForUpdate(diary.id())).thenReturn(Optional.of(diary));
        when(commentRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateCommentCommand(
            diary.id().value(), author, "답글", missingParent
        ))).isInstanceOf(CommentNotFoundException.class);
    }

    @Test
    void reply_to_reply_throws_400_depth_limit() {
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        Comment root = CommentTestFixtures.rootComment(diary.id(), author);
        Comment reply = CommentTestFixtures.reply(diary.id(), author, root.id());
        when(diaryRepository.findByIdForUpdate(diary.id())).thenReturn(Optional.of(diary));
        when(commentRepository.findById(reply.id())).thenReturn(Optional.of(reply));

        assertThatThrownBy(() -> service.create(new CreateCommentCommand(
            diary.id().value(), author, "답답글", reply.id().value()
        )))
            .isInstanceOf(InvalidCommentParentException.class)
            .hasMessageContaining("parent_must_be_root");

        verify(commentRepository, never()).save(any());
    }

    @Test
    void parent_diary_mismatch_throws_400() {
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        Diary otherDiary = CommentTestFixtures.publicDiary(author);
        Comment otherDiaryRoot = CommentTestFixtures.rootComment(otherDiary.id(), author);
        when(diaryRepository.findByIdForUpdate(diary.id())).thenReturn(Optional.of(diary));
        when(commentRepository.findById(otherDiaryRoot.id())).thenReturn(Optional.of(otherDiaryRoot));

        assertThatThrownBy(() -> service.create(new CreateCommentCommand(
            diary.id().value(), author, "답글", otherDiaryRoot.id().value()
        )))
            .isInstanceOf(InvalidCommentParentException.class)
            .hasMessageContaining("parent_diary_mismatch");
    }

    @Test
    void invalid_content_propagates_domain_exception_no_persist() {
        UUID author = UUID.randomUUID();
        UUID diaryId = UUID.randomUUID();
        assertThatThrownBy(() -> service.create(new CreateCommentCommand(
            diaryId, author, "  ", null
        ))).isInstanceOf(InvalidCommentContentException.class);

        verify(diaryRepository, never()).findById(any());
        verify(commentRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any());
    }

    @Test
    void user_summary_not_found_falls_back_to_unknown() {
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        when(diaryRepository.findByIdForUpdate(diary.id())).thenReturn(Optional.of(diary));
        when(userSummaryPort.get(author)).thenReturn(Optional.empty());

        CommentView view = service.create(new CreateCommentCommand(
            diary.id().value(), author, "ok", null
        ));

        assertThat(view.authorDisplayName()).isEqualTo("(unknown)");
    }

    @Test
    void reply_uses_root_parent_correctly() {
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        Comment root = CommentTestFixtures.rootComment(diary.id(), author);
        when(diaryRepository.findByIdForUpdate(diary.id())).thenReturn(Optional.of(diary));
        when(commentRepository.findById(root.id())).thenReturn(Optional.of(root));
        when(userSummaryPort.get(author))
            .thenReturn(Optional.of(new UserSummaryView(author, "user")));

        // 루트 댓글에 답글 — parent.isReply() = false 라 InvalidCommentParentException X
        CommentView view = service.create(new CreateCommentCommand(
            diary.id().value(), author, "답글", root.id().value()
        ));
        assertThat(view.parentId()).isEqualTo(root.id().value());
    }
}
