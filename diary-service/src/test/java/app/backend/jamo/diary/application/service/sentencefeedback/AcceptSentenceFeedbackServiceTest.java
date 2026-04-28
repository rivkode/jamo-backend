package app.backend.jamo.diary.application.service.sentencefeedback;

import app.backend.jamo.contracts.event.diary.SentenceFeedbackAccepted;
import app.backend.jamo.diary.application.dto.sentencefeedback.AcceptSentenceFeedbackCommand;
import app.backend.jamo.diary.application.dto.sentencefeedback.SentenceFeedbackResult;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackInvalidTransitionException;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackNotFoundException;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackUnknownSuggestionException;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedback;
import app.backend.jamo.diary.domain.model.sentencefeedback.Status;
import app.backend.jamo.diary.domain.model.sentencefeedback.Suggestion;
import app.backend.jamo.diary.domain.model.sentencefeedback.SuggestionId;
import app.backend.jamo.diary.domain.repository.OutboxEventPublisher;
import app.backend.jamo.diary.domain.repository.SentenceFeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

class AcceptSentenceFeedbackServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-28T10:00:00Z");

    private SentenceFeedbackRepository repository;
    private OutboxEventPublisher outboxEventPublisher;
    private TransactionTemplate transactionTemplate;
    private AcceptSentenceFeedbackService service;

    @BeforeEach
    void setUp() {
        repository = mock(SentenceFeedbackRepository.class);
        outboxEventPublisher = mock(OutboxEventPublisher.class);
        transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
            ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null)
        );
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AcceptSentenceFeedbackService(repository, outboxEventPublisher, transactionTemplate, clock);
    }

    @Test
    void happy_path_transitions_SUGGESTED_to_ACCEPTED_and_publishes_outbox() {
        UUID userId = UUID.randomUUID();
        SentenceFeedback fb = SentenceFeedbackTestFixtures.suggested(userId, NOW);
        Suggestion picked = fb.suggestions().get(0);

        when(repository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(fb));

        SentenceFeedbackResult result = service.accept(new AcceptSentenceFeedbackCommand(
            userId, fb.id().value(), picked.id().value()
        ));

        assertThat(result.status()).isEqualTo(Status.ACCEPTED.name());
        assertThat(result.decisionSuggestionId()).isEqualTo(picked.id().value());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(repository).save(fb);
        verify(repository).findByIdAndUserId(any(), any());
        verify(outboxEventPublisher).publish(eventCaptor.capture());
        verifyNoMoreInteractions(repository, outboxEventPublisher);

        SentenceFeedbackAccepted event = (SentenceFeedbackAccepted) eventCaptor.getValue();
        assertThat(event)
            .extracting(SentenceFeedbackAccepted::userId, SentenceFeedbackAccepted::feedbackId, SentenceFeedbackAccepted::suggestionId)
            .containsExactly(userId.toString(), fb.id().asString(), picked.id().asString());
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void missing_or_other_user_owned_returns_404_NotFound() {
        // §4 — 없거나 다른 사용자 소유 모두 findByIdAndUserId 가 empty 반환 (404 IDOR 통일).
        // 다른 user 검증의 실 동작 (JPA 쿼리) 은 Infrastructure slice (@DataJpaTest) 영역.
        when(repository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(new AcceptSentenceFeedbackCommand(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        ))).isInstanceOf(SentenceFeedbackNotFoundException.class);

        verify(repository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any());
    }

    @Test
    void unknown_suggestionId_throws_400() {
        UUID userId = UUID.randomUUID();
        SentenceFeedback fb = SentenceFeedbackTestFixtures.suggested(userId, NOW);
        when(repository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(fb));

        UUID unknownSuggestionId = UUID.randomUUID();
        assertThatThrownBy(() -> service.accept(new AcceptSentenceFeedbackCommand(
            userId, fb.id().value(), unknownSuggestionId
        ))).isInstanceOf(SentenceFeedbackUnknownSuggestionException.class);

        verify(repository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any());
    }

    @Test
    void final_status_throws_409_InvalidTransition() {
        UUID userId = UUID.randomUUID();
        SentenceFeedback fb = SentenceFeedbackTestFixtures.suggested(userId, NOW);
        Suggestion picked = fb.suggestions().get(0);
        fb.accept(SuggestionId.of(picked.id().value()), Clock.fixed(NOW, ZoneOffset.UTC));  // 이미 ACCEPTED
        when(repository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(fb));

        assertThatThrownBy(() -> service.accept(new AcceptSentenceFeedbackCommand(
            userId, fb.id().value(), picked.id().value()
        ))).isInstanceOf(SentenceFeedbackInvalidTransitionException.class);

        verify(repository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any());
    }
}
