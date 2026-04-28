package app.backend.jamo.diary.application.service.sentencefeedback;

import app.backend.jamo.contracts.event.diary.SentenceFeedbackRejected;
import app.backend.jamo.diary.application.dto.sentencefeedback.RejectSentenceFeedbackCommand;
import app.backend.jamo.diary.application.dto.sentencefeedback.SentenceFeedbackResult;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackInvalidTransitionException;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackNotFoundException;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedback;
import app.backend.jamo.diary.domain.model.sentencefeedback.Status;
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
import static org.mockito.Mockito.when;

class RejectSentenceFeedbackServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-28T10:00:00Z");

    private SentenceFeedbackRepository repository;
    private OutboxEventPublisher outboxEventPublisher;
    private TransactionTemplate transactionTemplate;
    private RejectSentenceFeedbackService service;

    @BeforeEach
    void setUp() {
        repository = mock(SentenceFeedbackRepository.class);
        outboxEventPublisher = mock(OutboxEventPublisher.class);
        transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
            ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null)
        );
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new RejectSentenceFeedbackService(repository, outboxEventPublisher, transactionTemplate, clock);
    }

    @Test
    void happy_path_with_reason_persists_REJECTED_and_publishes_outbox() {
        UUID userId = UUID.randomUUID();
        SentenceFeedback fb = SentenceFeedbackTestFixtures.suggested(userId, NOW);
        when(repository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(fb));

        SentenceFeedbackResult result = service.reject(new RejectSentenceFeedbackCommand(
            userId, fb.id().value(), "표현이 어색해요"
        ));

        assertThat(result.status()).isEqualTo(Status.REJECTED.name());

        ArgumentCaptor<SentenceFeedback> savedCaptor = ArgumentCaptor.forClass(SentenceFeedback.class);
        verify(repository).save(savedCaptor.capture());
        // M3 — 저장된 Aggregate 의 rejectionReason 검증 (자유 텍스트 보존)
        assertThat(savedCaptor.getValue().rejectionReason()).contains("표현이 어색해요");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisher).publish(eventCaptor.capture());
        SentenceFeedbackRejected event = (SentenceFeedbackRejected) eventCaptor.getValue();
        assertThat(event)
            .extracting(SentenceFeedbackRejected::userId, SentenceFeedbackRejected::feedbackId)
            .containsExactly(userId.toString(), fb.id().asString());
    }

    @Test
    void null_reason_is_normalized_and_REJECTED_persisted() {
        // §15 — reason 자유 텍스트, null/blank 허용 + Aggregate 가 null 정규화 (rejectionReason() = empty Optional)
        UUID userId = UUID.randomUUID();
        SentenceFeedback fb = SentenceFeedbackTestFixtures.suggested(userId, NOW);
        when(repository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(fb));

        SentenceFeedbackResult result = service.reject(new RejectSentenceFeedbackCommand(
            userId, fb.id().value(), null
        ));

        assertThat(result.status()).isEqualTo(Status.REJECTED.name());

        ArgumentCaptor<SentenceFeedback> savedCaptor = ArgumentCaptor.forClass(SentenceFeedback.class);
        verify(repository).save(savedCaptor.capture());
        // M3 — null reason 이 Aggregate 에 정규화되어 empty Optional 로 저장됨
        assertThat(savedCaptor.getValue().rejectionReason()).isEmpty();

        verify(outboxEventPublisher).publish(any(SentenceFeedbackRejected.class));
    }

    @Test
    void blank_reason_is_normalized_to_empty() {
        UUID userId = UUID.randomUUID();
        SentenceFeedback fb = SentenceFeedbackTestFixtures.suggested(userId, NOW);
        when(repository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(fb));

        service.reject(new RejectSentenceFeedbackCommand(userId, fb.id().value(), "   "));

        ArgumentCaptor<SentenceFeedback> savedCaptor = ArgumentCaptor.forClass(SentenceFeedback.class);
        verify(repository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().rejectionReason()).isEmpty();
    }

    @Test
    void missing_or_other_user_owned_returns_404() {
        when(repository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject(new RejectSentenceFeedbackCommand(
            UUID.randomUUID(), UUID.randomUUID(), "any"
        ))).isInstanceOf(SentenceFeedbackNotFoundException.class);

        verify(repository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any());
    }

    @Test
    void final_status_throws_409() {
        UUID userId = UUID.randomUUID();
        SentenceFeedback fb = SentenceFeedbackTestFixtures.suggested(userId, NOW);
        fb.reject("first", Clock.fixed(NOW, ZoneOffset.UTC));  // 이미 REJECTED
        when(repository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(fb));

        assertThatThrownBy(() -> service.reject(new RejectSentenceFeedbackCommand(
            userId, fb.id().value(), "second"
        ))).isInstanceOf(SentenceFeedbackInvalidTransitionException.class);

        verify(repository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any());
    }
}
