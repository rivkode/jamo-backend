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
import org.springframework.dao.DataIntegrityViolationException;
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
        // test-reviewer Low-1 — DELETE race 무관성 negative verify. race fallback 진입 시 exists 가
        // 2회 호출됨 (사전 체크 + fallback 재조회). 본 시나리오는 1회만 호출되어야 함.
        verify(diaryLikeRepository, org.mockito.Mockito.times(1))
            .existsByDiaryIdAndUserId(diary.id(), viewer);
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

    // ============================================================
    // 동시성 race + 멱등 fallback (cleanup PR — code-reviewer H1)
    // ============================================================

    @Test
    void liked_true_with_concurrent_race_falls_back_to_idempotent_read() {
        // 시나리오: 본 tx 가 existsByDiaryIdAndUserId=false 확인 후 INSERT 시도 → 다른 tx 가 먼저
        // INSERT 한 상태로 commit → 본 tx UNIQUE 위반 → DataIntegrityViolationException → 멱등 fallback
        // (Diary 재로드 + 실제 like 상태 재조회 → 다른 tx commit 후 진실 노출).
        UUID author = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        diary.onLikeAdded();  // 다른 tx commit 후 likeCount=1 시뮬레이션

        // mock TransactionTemplate: 1회차 throw (toggle 로직 race), 2회차 fallback 정상 fetch
        TransactionTemplate raceTxTemplate = mock(TransactionTemplate.class);
        when(raceTxTemplate.execute(any()))
            .thenAnswer(inv -> {
                throw new DataIntegrityViolationException("uk_diary_like_diary_user");
            })
            .thenAnswer(inv ->
                ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));

        ToggleDiaryLikeService raceService = new ToggleDiaryLikeService(
            diaryRepository, diaryLikeRepository, raceTxTemplate, DiaryTestFixtures.fixedClock());

        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        // code-reviewer H1 — fallback 안에서 실제 DB 상태 재조회 → true (다른 tx 가 INSERT 한 상태)
        when(diaryLikeRepository.existsByDiaryIdAndUserId(diary.id(), viewer)).thenReturn(true);

        ToggleDiaryLikeView view = raceService.toggle(
            new ToggleDiaryLikeCommand(diary.id().value(), viewer, true));

        assertThat(view.liked()).isTrue();
        assertThat(view.likeCount()).isEqualTo(1);  // 다른 tx commit 후 값
        // test-reviewer Medium-4 — fallback 진입 (transactionTemplate 2회 호출) 명시 검증
        verify(raceTxTemplate, org.mockito.Mockito.times(2)).execute(any());
    }

    @Test
    void liked_true_race_then_other_tx_deletes_returns_actual_DB_state() {
        // code-reviewer H1 — race 발생 후 fallback 시점에 또 다른 tx 가 DELETE 한 극단 케이스. 응답 liked
        // 는 command.liked echo 가 아닌 실제 DB 상태 (false) 노출 — UI 정합성 보장.
        UUID author = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        // 다른 tx 가 INSERT 후 또 다른 tx 가 DELETE 까지 commit → likeCount=0 / row 미존재

        TransactionTemplate raceTxTemplate = mock(TransactionTemplate.class);
        when(raceTxTemplate.execute(any()))
            .thenAnswer(inv -> {
                throw new DataIntegrityViolationException("uk_diary_like_diary_user");
            })
            .thenAnswer(inv ->
                ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));

        ToggleDiaryLikeService raceService = new ToggleDiaryLikeService(
            diaryRepository, diaryLikeRepository, raceTxTemplate, DiaryTestFixtures.fixedClock());

        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        // 또 다른 tx 가 DELETE — 실제 DB 상태는 false
        when(diaryLikeRepository.existsByDiaryIdAndUserId(diary.id(), viewer)).thenReturn(false);

        ToggleDiaryLikeView view = raceService.toggle(
            new ToggleDiaryLikeCommand(diary.id().value(), viewer, true));

        // command.liked=true 였지만 실제 DB 상태가 false 이므로 응답도 false (정합성)
        assertThat(view.liked()).isFalse();
        assertThat(view.likeCount()).isZero();
    }

    @Test
    void liked_true_with_concurrent_race_then_diary_deleted_throws_404() {
        // 극단 시나리오: race 발생 후 fallback 재로드 시점에 일기가 삭제됨 → 404
        UUID author = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);

        TransactionTemplate raceTxTemplate = mock(TransactionTemplate.class);
        when(raceTxTemplate.execute(any()))
            .thenAnswer(inv -> {
                throw new DataIntegrityViolationException("uk_diary_like_diary_user");
            })
            .thenAnswer(inv ->
                ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));

        ToggleDiaryLikeService raceService = new ToggleDiaryLikeService(
            diaryRepository, diaryLikeRepository, raceTxTemplate, DiaryTestFixtures.fixedClock());

        when(diaryRepository.findById(diary.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> raceService.toggle(
                new ToggleDiaryLikeCommand(diary.id().value(), viewer, true)))
            .isInstanceOf(DiaryNotFoundException.class);
    }
}
