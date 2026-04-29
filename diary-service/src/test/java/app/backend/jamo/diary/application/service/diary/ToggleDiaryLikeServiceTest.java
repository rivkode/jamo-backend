package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.diary.application.dto.diary.ToggleDiaryLikeCommand;
import app.backend.jamo.diary.application.dto.diary.ToggleDiaryLikeView;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diarylike.DiaryLike;
import app.backend.jamo.diary.domain.repository.DiaryLikeRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToggleDiaryLikeServiceTest {

    private DiaryRepository diaryRepository;
    private DiaryLikeRepository diaryLikeRepository;
    private ToggleDiaryLikeService service;

    @BeforeEach
    void setUp() {
        diaryRepository = mock(DiaryRepository.class);
        diaryLikeRepository = mock(DiaryLikeRepository.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
            ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null)
        );
        service = new ToggleDiaryLikeService(diaryRepository, diaryLikeRepository, transactionTemplate,
            DiaryTestFixtures.fixedClock());
    }

    @Test
    void liked_true_when_not_yet_liked_inserts_and_increments() {
        UUID author = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(diaryLikeRepository.existsByDiaryIdAndUserId(diary.id(), viewer)).thenReturn(false);

        ToggleDiaryLikeView view = service.toggle(new ToggleDiaryLikeCommand(diary.id().value(), viewer, true));

        assertThat(view.liked()).isTrue();
        assertThat(view.likeCount()).isEqualTo(1);
        verify(diaryLikeRepository).save(any(DiaryLike.class));
        verify(diaryRepository).save(diary);
    }

    @Test
    void liked_true_when_already_liked_is_no_op() {
        UUID author = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(diaryLikeRepository.existsByDiaryIdAndUserId(diary.id(), viewer)).thenReturn(true);

        ToggleDiaryLikeView view = service.toggle(new ToggleDiaryLikeCommand(diary.id().value(), viewer, true));

        assertThat(view.liked()).isTrue();
        assertThat(view.likeCount()).isZero();
        verify(diaryLikeRepository, never()).save(any());
        verify(diaryRepository, never()).save(any());
    }

    @Test
    void liked_true_when_already_liked_with_existing_count_does_not_increment() {
        // drift 검증 — 이미 좋아요인 상태에서 또 호출해도 likeCount 가 그대로 (1, not 2)
        UUID author = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        diary.onLikeAdded();  // 카운터 1
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(diaryLikeRepository.existsByDiaryIdAndUserId(diary.id(), viewer)).thenReturn(true);

        ToggleDiaryLikeView view = service.toggle(
            new ToggleDiaryLikeCommand(diary.id().value(), viewer, true));

        assertThat(view.likeCount()).isEqualTo(1);
        verify(diaryRepository, never()).save(any());
        verify(diaryLikeRepository, never()).save(any());
    }

    @Test
    void private_diary_with_author_self_can_toggle_like() {
        // 박제 §8 — 자기 비공개 일기 좋아요 허용 (일관성)
        UUID author = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.privateDiary(author);
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(diaryLikeRepository.existsByDiaryIdAndUserId(diary.id(), author)).thenReturn(false);

        ToggleDiaryLikeView view = service.toggle(
            new ToggleDiaryLikeCommand(diary.id().value(), author, true));

        assertThat(view.liked()).isTrue();
        assertThat(view.likeCount()).isEqualTo(1);
        verify(diaryLikeRepository).save(any());
    }

    @Test
    void liked_false_when_already_liked_deletes_and_decrements() {
        UUID author = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        diary.onLikeAdded();  // 카운터 1 시작
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(diaryLikeRepository.existsByDiaryIdAndUserId(diary.id(), viewer)).thenReturn(true);

        ToggleDiaryLikeView view = service.toggle(new ToggleDiaryLikeCommand(diary.id().value(), viewer, false));

        assertThat(view.liked()).isFalse();
        assertThat(view.likeCount()).isZero();
        verify(diaryLikeRepository).deleteByDiaryIdAndUserId(diary.id(), viewer);
        verify(diaryRepository).save(diary);
    }

    @Test
    void liked_false_when_not_yet_liked_is_no_op() {
        UUID author = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(diaryLikeRepository.existsByDiaryIdAndUserId(diary.id(), viewer)).thenReturn(false);

        ToggleDiaryLikeView view = service.toggle(new ToggleDiaryLikeCommand(diary.id().value(), viewer, false));

        assertThat(view.liked()).isFalse();
        assertThat(view.likeCount()).isZero();
        verify(diaryLikeRepository, never()).deleteByDiaryIdAndUserId(any(), any());
    }

    @Test
    void missing_diary_throws_404() {
        when(diaryRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggle(new ToggleDiaryLikeCommand(UUID.randomUUID(), UUID.randomUUID(), true)))
            .isInstanceOf(DiaryNotFoundException.class);
    }

    @Test
    void private_diary_with_other_viewer_throws_404() {
        UUID author = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.privateDiary(author);
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));

        assertThatThrownBy(() -> service.toggle(new ToggleDiaryLikeCommand(diary.id().value(), other, true)))
            .isInstanceOf(DiaryNotFoundException.class);
    }
}
