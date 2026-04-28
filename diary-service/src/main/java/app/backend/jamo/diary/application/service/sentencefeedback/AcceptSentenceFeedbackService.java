package app.backend.jamo.diary.application.service.sentencefeedback;

import app.backend.jamo.contracts.event.diary.SentenceFeedbackAccepted;
import app.backend.jamo.diary.application.dto.sentencefeedback.AcceptSentenceFeedbackCommand;
import app.backend.jamo.diary.application.dto.sentencefeedback.SentenceFeedbackResult;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackNotFoundException;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedback;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedbackId;
import app.backend.jamo.diary.domain.model.sentencefeedback.SuggestionId;
import app.backend.jamo.diary.domain.repository.OutboxEventPublisher;
import app.backend.jamo.diary.domain.repository.SentenceFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 제안 채택 use case (PRD diary/acceptSentenceFeedback.md §9).
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §4 (404 IDOR / final 상태 409 / unknown
 * suggestionId 400) / §12 (SentenceFeedbackAccepted Outbox).
 *
 * <p><b>단일 트랜잭션</b> — gRPC 호출 없음, DB 작업만. {@link SentenceFeedback#accept} 가 invariant 검증
 * 후 상태 전이 (SUGGESTED → ACCEPTED).
 *
 * <p>흐름:
 * <ol>
 *   <li>{@link SentenceFeedbackRepository#findByIdAndUserId} — 다른 사용자 소유면 empty → 404</li>
 *   <li>{@link SentenceFeedback#accept} — final 상태 → 409, unknown suggestionId → 400</li>
 *   <li>{@code save} + {@link OutboxEventPublisher#publish}({@link SentenceFeedbackAccepted})</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class AcceptSentenceFeedbackService {

    private final SentenceFeedbackRepository repository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public SentenceFeedbackResult accept(AcceptSentenceFeedbackCommand command) {

        SentenceFeedbackId feedbackId = SentenceFeedbackId.of(command.feedbackId());
        SuggestionId suggestionId = SuggestionId.of(command.suggestionId());

        SentenceFeedback updated = transactionTemplate.execute(status -> {
            SentenceFeedback fb = repository.findByIdAndUserId(feedbackId, command.userId())
                .orElseThrow(() -> new SentenceFeedbackNotFoundException(
                    "feedback not found or not owned: " + feedbackId.asString()
                ));
            fb.accept(suggestionId, clock);  // final → InvalidTransition 409 / unknown id → 400
            repository.save(fb);
            outboxEventPublisher.publish(new SentenceFeedbackAccepted(
                UUID.randomUUID().toString(),
                Instant.now(clock),
                feedbackId.asString(),
                command.userId().toString(),
                suggestionId.asString()
            ));
            return fb;
        });

        return SentenceFeedbackResult.from(updated, Instant.now(clock));
    }
}
