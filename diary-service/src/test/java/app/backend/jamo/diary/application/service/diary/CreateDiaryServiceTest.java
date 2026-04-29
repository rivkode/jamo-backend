package app.backend.jamo.diary.application.service.diary;

import app.backend.jamo.contracts.event.diary.DiaryCreated;
import app.backend.jamo.diary.application.dto.diary.CreateDiaryCommand;
import app.backend.jamo.diary.application.dto.diary.DiaryView;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.exception.InvalidDiaryContentException;
import app.backend.jamo.diary.domain.model.diary.Visibility;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import app.backend.jamo.diary.domain.repository.OutboxEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CreateDiaryServiceTest {

    private DiaryRepository diaryRepository;
    private OutboxEventPublisher outboxEventPublisher;
    private UserSummaryPort userSummaryPort;
    private CreateDiaryService service;

    @BeforeEach
    void setUp() {
        diaryRepository = mock(DiaryRepository.class);
        outboxEventPublisher = mock(OutboxEventPublisher.class);
        userSummaryPort = mock(UserSummaryPort.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
            ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null)
        );
        service = new CreateDiaryService(
            diaryRepository, outboxEventPublisher, userSummaryPort, transactionTemplate,
            DiaryTestFixtures.fixedClock()
        );
    }

    @Test
    void happy_path_persists_publishes_outbox_and_assembles_view() {
        UUID author = UUID.randomUUID();
        when(userSummaryPort.get(author))
            .thenReturn(Optional.of(new UserSummaryView(author, "홍길동")));

        CreateDiaryCommand cmd = new CreateDiaryCommand(
            author, "오늘 산책",
            List.of("https://cdn.example.com/a.jpg"),
            List.of("일상"),
            Visibility.PUBLIC
        );

        DiaryView view = service.create(cmd);

        assertThat(view.authorId()).isEqualTo(author);
        assertThat(view.authorDisplayName()).isEqualTo("홍길동");
        assertThat(view.content()).isEqualTo("오늘 산책");
        assertThat(view.images()).containsExactly("https://cdn.example.com/a.jpg");
        assertThat(view.tags()).containsExactly("일상");
        assertThat(view.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(view.likeCount()).isZero();
        assertThat(view.commentCount()).isZero();
        assertThat(view.likedByMe()).isFalse();
        assertThat(view.createdAt()).isEqualTo(DiaryTestFixtures.NOW);

        verify(diaryRepository).save(any());
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisher).publish(eventCaptor.capture());
        verifyNoMoreInteractions(diaryRepository, outboxEventPublisher);

        DiaryCreated event = (DiaryCreated) eventCaptor.getValue();
        assertThat(event.diaryId()).isEqualTo(view.diaryId().toString());
        assertThat(event.userId()).isEqualTo(author.toString());
        assertThat(event.occurredAt()).isEqualTo(DiaryTestFixtures.NOW);
        assertThat(event.eventId()).isNotBlank();
        assertThat(UUID.fromString(event.eventId())).isNotNull();  // UUID 형식
    }

    @Test
    void user_summary_not_found_falls_back_to_unknown() {
        UUID author = UUID.randomUUID();
        when(userSummaryPort.get(author)).thenReturn(Optional.empty());

        DiaryView view = service.create(new CreateDiaryCommand(
            author, "ok", List.of(), List.of(), Visibility.PRIVATE
        ));

        assertThat(view.authorDisplayName()).isEqualTo("(unknown)");
    }

    @Test
    void invalid_content_propagates_domain_exception_no_persist() {
        UUID author = UUID.randomUUID();
        assertThatThrownBy(() -> service.create(new CreateDiaryCommand(
            author, "  ", List.of(), List.of(), Visibility.PUBLIC
        ))).isInstanceOf(InvalidDiaryContentException.class);

        verify(diaryRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any());
    }
}
