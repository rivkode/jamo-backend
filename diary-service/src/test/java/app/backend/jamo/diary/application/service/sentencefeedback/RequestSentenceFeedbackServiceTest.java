package app.backend.jamo.diary.application.service.sentencefeedback;

import app.backend.jamo.contracts.event.diary.SentenceFeedbackRequested;
import app.backend.jamo.diary.application.dto.sentencefeedback.RequestSentenceFeedbackCommand;
import app.backend.jamo.diary.application.dto.sentencefeedback.SentenceFeedbackResult;
import app.backend.jamo.diary.domain.exception.InvalidSentenceTextException;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackNotFoundException;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedback;
import app.backend.jamo.diary.domain.model.sentencefeedback.Status;
import app.backend.jamo.diary.domain.model.sentencefeedback.Suggestion;
import app.backend.jamo.diary.domain.model.sentencefeedback.SuggestionId;
import app.backend.jamo.diary.domain.repository.OutboxEventPublisher;
import app.backend.jamo.diary.domain.repository.SentenceFeedbackAiGateway;
import app.backend.jamo.diary.domain.repository.SentenceFeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestSentenceFeedbackServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-28T10:00:00Z");

    private SentenceFeedbackRepository repository;
    private SentenceFeedbackAiGateway aiGateway;
    private OutboxEventPublisher outboxEventPublisher;
    private TransactionTemplate transactionTemplate;
    private RequestSentenceFeedbackService service;
    /** T1 의 첫 save 가 캡처한 Aggregate — T2 의 findById 응답으로 사용. */
    private AtomicReference<SentenceFeedback> firstSaved;

    @BeforeEach
    void setUp() {
        repository = mock(SentenceFeedbackRepository.class);
        aiGateway = mock(SentenceFeedbackAiGateway.class);
        outboxEventPublisher = mock(OutboxEventPublisher.class);
        transactionTemplate = mock(TransactionTemplate.class);
        firstSaved = new AtomicReference<>();

        // execute(callback) 가 callback 을 그대로 실행 — 단위 테스트는 트랜잭션 없음
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
            ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null)
        );
        // executeWithoutResult(Consumer) — mock 의 default 가 no-op 이라 명시 stub 필요
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> action = inv.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        // T1 save = 첫 save 만 firstSaved 에 캡처. T2 의 findById 가 그것을 그대로 반환.
        doAnswer(inv -> {
            firstSaved.compareAndSet(null, inv.getArgument(0));
            return null;
        }).when(repository).save(any());
        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(firstSaved.get()));

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new RequestSentenceFeedbackService(
            repository, aiGateway, outboxEventPublisher, transactionTemplate, clock
        );
    }

    @Test
    void SUGGESTED_response_persists_REQUESTED_then_SUGGESTED_with_outbox() {
        UUID userId = UUID.randomUUID();
        UUID diaryId = UUID.randomUUID();
        Suggestion s1 = new Suggestion(SuggestionId.newId(), "더 자연스러운 표현", "맥락 강조", 0.9);
        when(aiGateway.request(any())).thenReturn(SentenceFeedbackAiGateway.Result.suggested(List.of(s1)));

        SentenceFeedbackResult result = service.request(new RequestSentenceFeedbackCommand(
            userId, diaryId, "오늘 날씨 좋아요", List.of(), "casual"
        ));

        assertThat(result.status()).isEqualTo(Status.SUGGESTED.name());
        assertThat(result.suggestions()).hasSize(1);
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(24 * 3600));

        // T1 + T2 = 2 save / 1 outbox publish (T1 시점, §12)
        verify(repository, times(2)).save(any());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisher).publish(eventCaptor.capture());
        SentenceFeedbackRequested event = (SentenceFeedbackRequested) eventCaptor.getValue();
        assertThat(event)
            .extracting(SentenceFeedbackRequested::userId, SentenceFeedbackRequested::occurredAt)
            .containsExactly(userId.toString(), NOW);
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.feedbackId()).isEqualTo(firstSaved.get().id().asString());
    }

    @Test
    void FAILED_response_persists_REQUESTED_then_FAILED() {
        UUID userId = UUID.randomUUID();
        when(aiGateway.request(any())).thenReturn(SentenceFeedbackAiGateway.Result.failed("ai gateway timeout"));

        SentenceFeedbackResult result = service.request(new RequestSentenceFeedbackCommand(
            userId, null, "테스트 문장", List.of(), null
        ));

        assertThat(result.status()).isEqualTo(Status.FAILED.name());
        assertThat(result.suggestions()).isEmpty();
        verify(repository, times(2)).save(any());
        // §12 — Outbox 발행은 T1 시점이라 AI 응답 status 무관 (SUGGESTED/FAILED 동일하게 1건)
        verify(outboxEventPublisher, times(1)).publish(any(SentenceFeedbackRequested.class));
    }

    @Test
    void SentenceText_VO_invariant_violation_throws_before_persist() {
        when(aiGateway.request(any())).thenReturn(SentenceFeedbackAiGateway.Result.failed("never called"));

        assertThatThrownBy(() -> service.request(new RequestSentenceFeedbackCommand(
            UUID.randomUUID(), null, "  ", List.of(), null  // blank → 차단
        ))).isInstanceOf(InvalidSentenceTextException.class);

        verify(repository, times(0)).save(any());
        verify(outboxEventPublisher, times(0)).publish(any());
    }

    @Test
    void T2_findById_missing_throws_NotFoundException() {
        when(aiGateway.request(any())).thenReturn(SentenceFeedbackAiGateway.Result.failed("any"));
        // firstSaved 를 null 로 유지 — T2 의 findById 가 empty
        firstSaved.set(null);
        when(repository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(new RequestSentenceFeedbackCommand(
            UUID.randomUUID(), null, "테스트", List.of(), null
        ))).isInstanceOf(SentenceFeedbackNotFoundException.class);
    }

    @Test
    void unknown_tone_string_falls_back_to_chat_service_default() {
        when(aiGateway.request(any())).thenReturn(SentenceFeedbackAiGateway.Result.failed("any"));

        service.request(new RequestSentenceFeedbackCommand(
            UUID.randomUUID(), null, "문장", List.of(), "wat"  // unknown
        ));

        // §10 forward 호환 — unknown → null (chat-service default)
        ArgumentCaptor<SentenceFeedbackAiGateway.Args> argsCaptor =
            ArgumentCaptor.forClass(SentenceFeedbackAiGateway.Args.class);
        verify(aiGateway).request(argsCaptor.capture());
        assertThat(argsCaptor.getValue().toneOrNull()).isNull();
    }

    @Test
    void casual_tone_maps_to_Tone_CASUAL() {
        when(aiGateway.request(any())).thenReturn(SentenceFeedbackAiGateway.Result.failed("any"));

        service.request(new RequestSentenceFeedbackCommand(
            UUID.randomUUID(), null, "문장", List.of(), "CASUAL"  // case-insensitive
        ));

        ArgumentCaptor<SentenceFeedbackAiGateway.Args> argsCaptor =
            ArgumentCaptor.forClass(SentenceFeedbackAiGateway.Args.class);
        verify(aiGateway).request(argsCaptor.capture());
        assertThat(argsCaptor.getValue().toneOrNull())
            .isEqualTo(app.backend.jamo.diary.domain.model.sentencefeedback.Tone.CASUAL);
    }
}
