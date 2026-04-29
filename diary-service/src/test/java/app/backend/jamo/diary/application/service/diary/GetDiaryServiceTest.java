package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.diary.application.dto.diary.DiaryView;
import app.backend.jamo.diary.application.dto.diary.GetDiaryQuery;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.repository.DiaryLikeRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetDiaryServiceTest {

    private DiaryRepository diaryRepository;
    private DiaryLikeRepository diaryLikeRepository;
    private UserSummaryPort userSummaryPort;
    private GetDiaryService service;

    @BeforeEach
    void setUp() {
        diaryRepository = mock(DiaryRepository.class);
        diaryLikeRepository = mock(DiaryLikeRepository.class);
        userSummaryPort = mock(UserSummaryPort.class);
        service = new GetDiaryService(diaryRepository, diaryLikeRepository, userSummaryPort);
    }

    @Test
    void public_diary_is_accessible_by_anyone_with_likedByMe_true() {
        UUID author = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(diaryLikeRepository.existsByDiaryIdAndUserId(diary.id(), viewer)).thenReturn(true);
        when(userSummaryPort.get(author)).thenReturn(Optional.of(new UserSummaryView(author, "홍길동")));

        DiaryView view = service.get(new GetDiaryQuery(diary.id().value(), viewer));

        assertThat(view.likedByMe()).isTrue();
        assertThat(view.authorDisplayName()).isEqualTo("홍길동");
    }

    @Test
    void private_diary_with_author_viewer_is_accessible() {
        UUID author = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.privateDiary(author);
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(diaryLikeRepository.existsByDiaryIdAndUserId(any(), any())).thenReturn(false);
        when(userSummaryPort.get(author)).thenReturn(Optional.of(new UserSummaryView(author, "내")));

        DiaryView view = service.get(new GetDiaryQuery(diary.id().value(), author));

        assertThat(view.diaryId()).isEqualTo(diary.id().value());
        assertThat(view.likedByMe()).isFalse();
    }

    @Test
    void private_diary_with_other_viewer_throws_not_found() {
        UUID author = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.privateDiary(author);
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));

        assertThatThrownBy(() -> service.get(new GetDiaryQuery(diary.id().value(), other)))
            .isInstanceOf(DiaryNotFoundException.class);
    }

    @Test
    void missing_diary_throws_not_found() {
        UUID id = UUID.randomUUID();
        when(diaryRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(new GetDiaryQuery(id, UUID.randomUUID())))
            .isInstanceOf(DiaryNotFoundException.class);
    }

    @Test
    void user_summary_not_found_falls_back_to_unknown() {
        UUID author = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));
        when(diaryLikeRepository.existsByDiaryIdAndUserId(any(), any())).thenReturn(false);
        when(userSummaryPort.get(author)).thenReturn(Optional.empty());

        DiaryView view = service.get(new GetDiaryQuery(diary.id().value(), UUID.randomUUID()));
        assertThat(view.authorDisplayName()).isEqualTo("(unknown)");
    }
}
