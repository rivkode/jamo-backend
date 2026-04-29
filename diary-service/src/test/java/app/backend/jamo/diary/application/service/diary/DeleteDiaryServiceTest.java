package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.contracts.event.diary.DiaryDeleted;
import app.backend.jamo.diary.application.dto.diary.DeleteDiaryCommand;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import app.backend.jamo.diary.domain.repository.OutboxEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DeleteDiaryServiceTest {

    private DiaryRepository diaryRepository;
    private OutboxEventPublisher outboxEventPublisher;
    private DeleteDiaryService service;

    @BeforeEach
    void setUp() {
        diaryRepository = mock(DiaryRepository.class);
        outboxEventPublisher = mock(OutboxEventPublisher.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> action = inv.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        service = new DeleteDiaryService(diaryRepository, outboxEventPublisher, transactionTemplate,
            DiaryTestFixtures.fixedClock());
    }

    @Test
    void happy_path_deletes_and_publishes_outbox() {
        UUID author = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));

        service.delete(new DeleteDiaryCommand(diary.id().value(), author));

        verify(diaryRepository).findById(diary.id());
        verify(diaryRepository).deleteById(diary.id());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisher).publish(captor.capture());
        verifyNoMoreInteractions(diaryRepository, outboxEventPublisher);

        DiaryDeleted event = (DiaryDeleted) captor.getValue();
        assertThat(event.diaryId()).isEqualTo(diary.id().asString());
        assertThat(event.userId()).isEqualTo(author.toString());
        assertThat(event.occurredAt()).isEqualTo(DiaryTestFixtures.NOW);
        assertThat(event.eventId()).isNotBlank();
        assertThat(UUID.fromString(event.eventId())).isNotNull();
    }

    @Test
    void missing_diary_throws_404_no_publish() {
        when(diaryRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(new DeleteDiaryCommand(UUID.randomUUID(), UUID.randomUUID())))
            .isInstanceOf(DiaryNotFoundException.class);
        verify(diaryRepository, never()).deleteById(any());
        verify(outboxEventPublisher, never()).publish(any());
    }

    @Test
    void non_owner_throws_404_no_publish() {
        UUID author = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Diary diary = DiaryTestFixtures.publicDiary(author);
        when(diaryRepository.findById(diary.id())).thenReturn(Optional.of(diary));

        assertThatThrownBy(() -> service.delete(new DeleteDiaryCommand(diary.id().value(), other)))
            .isInstanceOf(DiaryNotFoundException.class);
        verify(diaryRepository, never()).deleteById(any());
        verify(outboxEventPublisher, never()).publish(any());
    }
}
