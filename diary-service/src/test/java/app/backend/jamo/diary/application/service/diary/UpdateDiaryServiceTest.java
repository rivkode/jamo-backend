package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.diary.application.dto.diary.DiaryView;
import app.backend.jamo.diary.application.dto.diary.UpdateDiaryCommand;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.exception.InvalidLineCountException;
import app.backend.jamo.diary.domain.exception.InvalidLineLengthException;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.Visibility;
import app.backend.jamo.diary.domain.repository.DiaryLikeRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateDiaryServiceTest {

    private DiaryRepository diaryRepository;
    private DiaryLikeRepository diaryLikeRepository;
    private UserSummaryPort userSummaryPort;
    private UpdateDiaryService service;

    @BeforeEach
    void setUp() {
        diaryRepository = mock(DiaryRepository.class);
        diaryLikeRepository = mock(DiaryLikeRepository.class);
        userSummaryPort = mock(UserSummaryPort.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> action = inv.getArgument(0);
            return action.doInTransaction(null);
        }).when(transactionTemplate).execute(any());
        service = new UpdateDiaryService(
            diaryRepository, diaryLikeRepository, userSummaryPort, transactionTemplate);
    }

    @Test
    void updates_diary_and_returns_view_with_new_fields_when_called_by_author() {
        UUID author = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        when(diaryRepository.findByIdForUpdate(diary.id())).thenReturn(Optional.of(diary));
        when(diaryLikeRepository.existsByDiaryIdAndUserId(eq(diary.id()), eq(author))).thenReturn(false);
        when(userSummaryPort.get(author)).thenReturn(
            Optional.of(new UserSummaryView(author, "홍길동")));

        DiaryView view = service.update(new UpdateDiaryCommand(
            diary.id().value(), author,
            List.of("수정된 본문", "둘째 줄", "셋째 줄"), List.of("https://e.io/x.png"), List.of("새태그"),
            Visibility.PRIVATE));

        verify(diaryRepository).findByIdForUpdate(diary.id());
        verify(diaryRepository).save(diary);
        assertThat(view.lines()).containsExactly("수정된 본문", "둘째 줄", "셋째 줄");
        assertThat(view.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(view.images()).containsExactly("https://e.io/x.png");
        assertThat(view.tags()).containsExactly("새태그");
        assertThat(view.authorDisplayName()).isEqualTo("홍길동");
        assertThat(view.likedByMe()).isFalse();
    }

    @Test
    void preserves_counters_and_createdAt_after_update() {
        UUID author = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        diary.onLikeAdded();
        diary.onLikeAdded();
        diary.onCommentAdded();
        int beforeLikes = diary.likeCount();
        int beforeComments = diary.commentCount();
        when(diaryRepository.findByIdForUpdate(any())).thenReturn(Optional.of(diary));
        when(diaryLikeRepository.existsByDiaryIdAndUserId(any(), any())).thenReturn(false);
        when(userSummaryPort.get(any())).thenReturn(Optional.empty());

        DiaryView view = service.update(new UpdateDiaryCommand(
            diary.id().value(), author,
            List.of("수정", "둘째", "셋째"), List.of(), List.of(), Visibility.PUBLIC));

        assertThat(view.likeCount()).isEqualTo(beforeLikes);
        assertThat(view.commentCount()).isEqualTo(beforeComments);
        assertThat(view.createdAt()).isEqualTo(DiaryTestFixtures.NOW);
    }

    @Test
    void exposes_likedByMe_false_when_no_like_row() {
        // test-reviewer M4 — false 케이스 명시 분리 (의도 가독성).
        UUID author = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        when(diaryRepository.findByIdForUpdate(any())).thenReturn(Optional.of(diary));
        when(diaryLikeRepository.existsByDiaryIdAndUserId(eq(diary.id()), eq(author))).thenReturn(false);
        when(userSummaryPort.get(any())).thenReturn(Optional.empty());

        DiaryView view = service.update(new UpdateDiaryCommand(
            diary.id().value(), author,
            List.of("수정", "둘째", "셋째"), List.of(), List.of(), Visibility.PUBLIC));

        assertThat(view.likedByMe()).isFalse();
    }

    @Test
    void exposes_likedByMe_true_when_author_has_liked_own_diary() {
        // 자기 일기에 좋아요 누른 상태 — Aggregate.isAccessibleBy 와 다르게 좋아요는 본인도 가능 (박제 §8).
        // test-reviewer 누락 가능성: likeCount 도 일치하도록 onLikeAdded() 호출 — 자연스러운 fixture.
        UUID author = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        diary.onLikeAdded();
        when(diaryRepository.findByIdForUpdate(any())).thenReturn(Optional.of(diary));
        when(diaryLikeRepository.existsByDiaryIdAndUserId(eq(diary.id()), eq(author))).thenReturn(true);
        when(userSummaryPort.get(any())).thenReturn(Optional.empty());

        DiaryView view = service.update(new UpdateDiaryCommand(
            diary.id().value(), author,
            List.of("수정", "둘째", "셋째"), List.of(), List.of(), Visibility.PUBLIC));

        assertThat(view.likedByMe()).isTrue();
        assertThat(view.likeCount()).isEqualTo(1);
    }

    @Test
    void missing_diary_throws_DiaryNotFoundException_no_save() {
        when(diaryRepository.findByIdForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(new UpdateDiaryCommand(
            UUID.randomUUID(), UUID.randomUUID(),
            List.of("수정", "둘째", "셋째"), List.of(), List.of(), Visibility.PUBLIC)))
            .isInstanceOf(DiaryNotFoundException.class);

        verify(diaryRepository, never()).save(any());
    }

    @Test
    void non_author_editor_throws_DiaryNotFoundException_for_IDOR_no_save() {
        // Q-S3a-1 (사용자 결정 404 IDOR): code-reviewer H1 fix 후 — Application 의 isOwnedBy 검증이 VO 생성
        // 보다 먼저 NotFound 로 던져 IDOR 누출 차단. AccessDenied 는 도메인 invariant 깊이의 안전망.
        UUID author = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        java.util.List<String> beforeLines = diary.lines().values();
        Visibility beforeVisibility = diary.visibility();
        when(diaryRepository.findByIdForUpdate(any())).thenReturn(Optional.of(diary));

        assertThatThrownBy(() -> service.update(new UpdateDiaryCommand(
            diary.id().value(), otherUser,
            List.of("수정", "둘째", "셋째"), List.of(), List.of(), Visibility.PUBLIC)))
            .isInstanceOf(DiaryNotFoundException.class);

        verify(diaryRepository, never()).save(any());
        // test-reviewer M2 — atomic 보존: 비작성자 시도 후 Aggregate state 유지
        assertThat(diary.lines().values()).isEqualTo(beforeLines);
        assertThat(diary.visibility()).isEqualTo(beforeVisibility);
    }

    @Test
    void non_author_editor_with_invalid_body_still_returns_404_IDOR_not_400() {
        // 핵심 IDOR 회귀 신호: 비작성자가 invalid body (blank content) 를 보내도 400 (validation) 이 아닌
        // 404 (NotFound) 가 응답되어야 한다. VO 생성이 ownership 검증보다 앞서면 깨지는 시나리오.
        UUID author = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        when(diaryRepository.findByIdForUpdate(any())).thenReturn(Optional.of(diary));

        assertThatThrownBy(() -> service.update(new UpdateDiaryCommand(
            diary.id().value(), otherUser,
            List.of("  ", "둘째", "셋째"), List.of(), List.of(), Visibility.PUBLIC)))
            .isInstanceOf(DiaryNotFoundException.class);

        verify(diaryRepository, never()).save(any());
    }

    @Test
    void line_count_not_three_throws_InvalidLineCount_when_called_by_author() {
        // test-reviewer M2 — 작성자가 2줄 보낼 시 ownership 통과 후 VO 생성 단계에서 422 (InvalidLineCount).
        UUID author = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        when(diaryRepository.findByIdForUpdate(any())).thenReturn(Optional.of(diary));

        assertThatThrownBy(() -> service.update(new UpdateDiaryCommand(
            diary.id().value(), author,
            List.of("한 줄", "두 줄"), List.of(), List.of(), Visibility.PUBLIC)))
            .isInstanceOf(InvalidLineCountException.class);

        verify(diaryRepository, never()).save(any());
    }

    @Test
    void invalid_line_VO_throws_400_when_called_by_author() {
        // 작성자가 invalid body 를 보내면 ownership 통과 후 VO 생성 단계에서 400 (InvalidLineLength).
        UUID author = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        when(diaryRepository.findByIdForUpdate(any())).thenReturn(Optional.of(diary));

        assertThatThrownBy(() -> service.update(new UpdateDiaryCommand(
            diary.id().value(), author,
            List.of("  ", "둘째", "셋째"), List.of(), List.of(), Visibility.PUBLIC)))
            .isInstanceOf(InvalidLineLengthException.class);

        verify(diaryRepository, never()).save(any());
    }
}
