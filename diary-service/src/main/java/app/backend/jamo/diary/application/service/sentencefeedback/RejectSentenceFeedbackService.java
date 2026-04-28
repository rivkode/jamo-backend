package app.backend.jamo.diary.application.service.sentencefeedback;

import app.backend.jamo.contracts.event.diary.SentenceFeedbackRejected;
import app.backend.jamo.diary.application.dto.sentencefeedback.RejectSentenceFeedbackCommand;
import app.backend.jamo.diary.application.dto.sentencefeedback.SentenceFeedbackResult;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackNotFoundException;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedback;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedbackId;
import app.backend.jamo.diary.domain.repository.OutboxEventPublisher;
import app.backend.jamo.diary.domain.repository.SentenceFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 제안 거부 use case (PRD diary/rejectSentenceFeedback.md §9).
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §4 (404 IDOR / final 409) /
 * §12 (SentenceFeedbackRejected Outbox 발행 채택 — Open Item 해소) / §15 (reason 자유 텍스트, null 허용).
 *
 * <p><b>단일 트랜잭션</b> — DB 작업만. {@link SentenceFeedback#reject} 가 reason 정규화 + 상태 전이
 * (SUGGESTED → REJECTED).
 */
@Service
@RequiredArgsConstructor
public class RejectSentenceFeedbackService {

    private final SentenceFeedbackRepository repository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public SentenceFeedbackResult reject(RejectSentenceFeedbackCommand command) {
        SentenceFeedbackId feedbackId = SentenceFeedbackId.of(command.feedbackId());

        SentenceFeedback updated = transactionTemplate.execute(status -> {
            SentenceFeedback fb = repository.findByIdAndUserId(feedbackId, command.userId())
                .orElseThrow(() -> new SentenceFeedbackNotFoundException(
                    "feedback not found or not owned: " + feedbackId.asString()
                ));
            fb.reject(command.reasonOrNull(), clock);  // final → InvalidTransition 409
            repository.save(fb);
            outboxEventPublisher.publish(new SentenceFeedbackRejected(
                UUID.randomUUID().toString(),
                Instant.now(clock),
                feedbackId.asString(),
                command.userId().toString()
            ));
            return fb;
        });

        return SentenceFeedbackResult.from(updated, Instant.now(clock));
    }
}
