package app.backend.jamo.diary.application.service.comment;

import app.backend.jamo.diary.application.cursor.CommentCursorCodec;
import app.backend.jamo.diary.application.cursor.InvalidCommentCursorException;
import app.backend.jamo.diary.application.dto.comment.CommentListView;
import app.backend.jamo.diary.application.dto.comment.ListCommentsQuery;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.model.comment.Comment;
import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.repository.CommentLikeRepository;
import app.backend.jamo.diary.domain.repository.CommentRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import app.backend.jamo.diary.domain.repository.cursor.CommentCursor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListCommentsServiceTest {

    private CommentRepository commentRepository;
    private CommentLikeRepository commentLikeRepository;
    private DiaryRepository diaryRepository;
    private UserSummaryPort userSummaryPort;
    private ListCommentsService service;

    @BeforeEach
    void setUp() {
        commentRepository = mock(CommentRepository.class);
        commentLikeRepository = mock(CommentLikeRepository.class);
        diaryRepository = mock(DiaryRepository.class);
        userSummaryPort = mock(UserSummaryPort.class);
        service = new ListCommentsService(
            commentRepository, commentLikeRepository, diaryRepository, userSummaryPort
        );
    }

    @Test
    void empty_page_returns_empty_view_with_no_next() {
        UUID viewer = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(viewer);
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(commentRepository.findByDiaryId(eq(diary.id()), any(), anyInt()))
            .thenReturn(List.of());

        CommentListView view = service.list(new ListCommentsQuery(
            diary.id().value(), viewer, null, 20
        ));

        assertThat(view.items()).isEmpty();
        assertThat(view.nextCursor()).isNull();
        assertThat(view.hasNext()).isFalse();
        verify(commentLikeRepository, never()).findCommentIdsLikedByUser(any(), any());
        verify(userSummaryPort, never()).batchGet(any());
    }

    @Test
    void single_page_assembles_view_with_user_summary_and_liked_set() {
        UUID viewer = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        Comment c1 = CommentTestFixtures.rootComment(diary.id(), author);
        Comment c2 = CommentTestFixtures.commentWithLikes(diary.id(), author, null, 5);

        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(commentRepository.findByDiaryId(eq(diary.id()), any(), eq(21)))
            .thenReturn(List.of(c1, c2));
        when(commentLikeRepository.findCommentIdsLikedByUser(eq(viewer), any()))
            .thenReturn(Set.of(c2.id()));
        when(userSummaryPort.batchGet(any()))
            .thenReturn(Map.of(author, new UserSummaryView(author, "Alice")));

        CommentListView view = service.list(new ListCommentsQuery(
            diary.id().value(), viewer, null, 20
        ));

        assertThat(view.items()).hasSize(2);
        assertThat(view.items().get(0).authorDisplayName()).isEqualTo("Alice");
        assertThat(view.items().get(0).likedByMe()).isFalse();
        assertThat(view.items().get(1).likeCount()).isEqualTo(5);
        assertThat(view.items().get(1).likedByMe()).isTrue();
        assertThat(view.hasNext()).isFalse();
        assertThat(view.nextCursor()).isNull();
    }

    @Test
    void hasNext_when_size_plus_1_returned_and_encodes_cursor() {
        UUID viewer = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        Comment c1 = CommentTestFixtures.rootComment(diary.id(), author);
        Comment c2 = CommentTestFixtures.rootComment(diary.id(), author);
        Comment c3 = CommentTestFixtures.rootComment(diary.id(), author);

        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(commentRepository.findByDiaryId(eq(diary.id()), any(), eq(3))) // size=2 → fetch=3
            .thenReturn(List.of(c1, c2, c3));
        when(commentLikeRepository.findCommentIdsLikedByUser(eq(viewer), any()))
            .thenReturn(Set.of());
        when(userSummaryPort.batchGet(any()))
            .thenReturn(Map.of(author, new UserSummaryView(author, "Alice")));

        CommentListView view = service.list(new ListCommentsQuery(
            diary.id().value(), viewer, null, 2
        ));

        assertThat(view.items()).hasSize(2);
        assertThat(view.items().get(0).commentId()).isEqualTo(c1.id().value());
        assertThat(view.items().get(1).commentId()).isEqualTo(c2.id().value());
        assertThat(view.hasNext()).isTrue();
        assertThat(view.nextCursor()).isNotNull();

        // 디코드 round-trip — c2 (페이지 마지막) 의 createdAt + id 가 cursor 에
        CommentCursor decoded = CommentCursorCodec.decode(view.nextCursor());
        assertThat(decoded.lastCommentId()).isEqualTo(c2.id());
    }

    @Test
    void cursor_decode_passed_to_repository() {
        UUID viewer = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(viewer);
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(commentRepository.findByDiaryId(eq(diary.id()), any(CommentCursor.class), anyInt()))
            .thenReturn(List.of());

        CommentCursor cursor = new CommentCursor(CommentTestFixtures.NOW, CommentId.newId());
        String encoded = CommentCursorCodec.encode(cursor);

        service.list(new ListCommentsQuery(diary.id().value(), viewer, encoded, 20));

        ArgumentCaptor<CommentCursor> captor = ArgumentCaptor.forClass(CommentCursor.class);
        verify(commentRepository).findByDiaryId(eq(diary.id()), captor.capture(), eq(21));
        assertThat(captor.getValue()).isEqualTo(cursor);
    }

    @Test
    void blank_cursor_is_treated_as_first_page() {
        UUID viewer = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(viewer);
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(commentRepository.findByDiaryId(eq(diary.id()), eq(null), eq(21)))
            .thenReturn(List.of());

        service.list(new ListCommentsQuery(diary.id().value(), viewer, "   ", 20));

        verify(commentRepository).findByDiaryId(diary.id(), null, 21);
    }

    @Test
    void diary_not_found_throws_404() {
        UUID viewer = UUID.randomUUID();
        UUID diaryId = UUID.randomUUID();
        when(diaryRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(new ListCommentsQuery(diaryId, viewer, null, 20)))
            .isInstanceOf(DiaryNotFoundException.class);
    }

    @Test
    void private_diary_non_author_throws_404_idor() {
        UUID author = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        Diary privateDiary = CommentTestFixtures.privateDiary(author);
        when(diaryRepository.findById(privateDiary.id())).thenReturn(Optional.of(privateDiary));

        assertThatThrownBy(() -> service.list(new ListCommentsQuery(
            privateDiary.id().value(), otherUser, null, 20
        ))).isInstanceOf(DiaryNotFoundException.class);
    }

    @Test
    void invalid_cursor_propagates_400() {
        UUID viewer = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(viewer);
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));

        assertThatThrownBy(() -> service.list(new ListCommentsQuery(
            diary.id().value(), viewer, "***bad-cursor***", 20
        ))).isInstanceOf(InvalidCommentCursorException.class);
    }

    @Test
    void parent_id_propagates_to_view_for_replies() {
        UUID viewer = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author);
        Comment root = CommentTestFixtures.rootComment(diary.id(), author);
        Comment reply = CommentTestFixtures.reply(diary.id(), author, root.id());
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(commentRepository.findByDiaryId(eq(diary.id()), any(), anyInt()))
            .thenReturn(List.of(root, reply));
        when(commentLikeRepository.findCommentIdsLikedByUser(eq(viewer), any()))
            .thenReturn(Set.of());
        when(userSummaryPort.batchGet(any()))
            .thenReturn(Map.of(author, new UserSummaryView(author, "Alice")));

        CommentListView view = service.list(new ListCommentsQuery(
            diary.id().value(), viewer, null, 20
        ));

        assertThat(view.items().get(0).parentId()).isNull();
        assertThat(view.items().get(1).parentId()).isEqualTo(root.id().value());
    }

    @Test
    void unknown_user_summary_falls_back_to_unknown_label() {
        UUID viewer = UUID.randomUUID();
        UUID author1 = UUID.randomUUID();
        UUID author2 = UUID.randomUUID();
        Diary diary = CommentTestFixtures.publicDiary(author1);
        Comment c1 = CommentTestFixtures.rootComment(diary.id(), author1);
        Comment c2 = CommentTestFixtures.rootComment(diary.id(), author2);

        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(commentRepository.findByDiaryId(eq(diary.id()), any(), anyInt()))
            .thenReturn(List.of(c1, c2));
        when(commentLikeRepository.findCommentIdsLikedByUser(eq(viewer), any()))
            .thenReturn(Set.of());
        // author2 만 누락 (NOT_FOUND fallback)
        when(userSummaryPort.batchGet(any()))
            .thenReturn(Map.of(author1, new UserSummaryView(author1, "Alice")));

        CommentListView view = service.list(new ListCommentsQuery(
            diary.id().value(), viewer, null, 20
        ));

        assertThat(view.items().get(0).authorDisplayName()).isEqualTo("Alice");
        assertThat(view.items().get(1).authorDisplayName()).isEqualTo("(unknown)");
    }
}
