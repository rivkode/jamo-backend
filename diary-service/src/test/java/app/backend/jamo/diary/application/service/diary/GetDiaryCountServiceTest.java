package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.diary.application.dto.diary.GetDiaryCountQuery;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetDiaryCountServiceTest {

    private DiaryRepository diaryRepository;
    private GetDiaryCountService service;

    @BeforeEach
    void setUp() {
        diaryRepository = mock(DiaryRepository.class);
        service = new GetDiaryCountService(diaryRepository);
    }

    @Test
    void includePrivate_true_counts_all_diaries() {
        UUID author = UUID.randomUUID();
        when(diaryRepository.countByAuthorId(author)).thenReturn(12L);

        long count = service.count(new GetDiaryCountQuery(author, true));

        assertThat(count).isEqualTo(12L);
        verify(diaryRepository).countByAuthorId(author);
        verify(diaryRepository, never()).countPublicByAuthorId(author);
    }

    @Test
    void includePrivate_false_counts_public_only() {
        UUID author = UUID.randomUUID();
        when(diaryRepository.countPublicByAuthorId(author)).thenReturn(5L);

        long count = service.count(new GetDiaryCountQuery(author, false));

        assertThat(count).isEqualTo(5L);
        verify(diaryRepository).countPublicByAuthorId(author);
        verify(diaryRepository, never()).countByAuthorId(author);
    }

    @Test
    void zero_when_no_diaries() {
        UUID author = UUID.randomUUID();
        when(diaryRepository.countByAuthorId(author)).thenReturn(0L);

        assertThat(service.count(new GetDiaryCountQuery(author, true))).isZero();
    }
}
