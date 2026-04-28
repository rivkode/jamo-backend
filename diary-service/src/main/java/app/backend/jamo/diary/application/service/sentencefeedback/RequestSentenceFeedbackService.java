package app.backend.jamo.diary.application.service.sentencefeedback;

import app.backend.jamo.contracts.event.diary.SentenceFeedbackRequested;
import app.backend.jamo.diary.application.dto.sentencefeedback.RequestSentenceFeedbackCommand;
import app.backend.jamo.diary.application.dto.sentencefeedback.SentenceFeedbackResult;
import app.backend.jamo.diary.domain.exception.SentenceFeedbackNotFoundException;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedback;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedbackId;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceText;
import app.backend.jamo.diary.domain.model.sentencefeedback.Tone;
import app.backend.jamo.diary.domain.repository.OutboxEventPublisher;
import app.backend.jamo.diary.domain.repository.SentenceFeedbackAiGateway;
import app.backend.jamo.diary.domain.repository.SentenceFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 문장 피드백 요청 use case (PRD diary/requestSentenceFeedback.md §9).
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §3 (TTL 24h) / §6 (chat-service Deadline 35s) /
 * §7 (FAILED → 200 + fallback) / §12 (SentenceFeedbackRequested Outbox).
 *
 * <p><b>2-트랜잭션 분리</b> (RegisterUserService BCrypt 패턴 정합):
 * <ol>
 *   <li><b>T1 (DB)</b>: VO 검증 → {@link SentenceFeedback#request} (REQUESTED) → {@code save} +
 *       {@link OutboxEventPublisher#publish}({@link SentenceFeedbackRequested}). COMMIT.</li>
 *   <li><b>트랜잭션 외부</b> (~35s): {@link SentenceFeedbackAiGateway#request}. gRPC Deadline 35s 가 단일
 *       트랜잭션 안에 있으면 DB 커넥션 풀 점유 위험.</li>
 *   <li><b>T2 (DB)</b>: Aggregate 다시 로드 → {@code Result.Status} 분기 ({@code markSuggested} 또는
 *       {@code markFailed}) → {@code save}. COMMIT. (이벤트 추가 발행 X — status 전이만)</li>
 * </ol>
 *
 * <p>Aggregate 가 T1 commit 시점에 영속되므로 chat-service 호출 실패 / 서버 다운 시점에도 ID 발급 + 추적
 * 가능. 후속 배치 (D-a-5-impl-batch) 가 stuck-in-REQUESTED row 도 EXPIRED 처리 가능.
 *
 * <p>fallback 정책: AiGateway 가 시스템 오류를 자체 {@link SentenceFeedbackAiGateway.Result.Status#FAILED}
 * 로 변환해 반환 (Adapter 책임) — 본 Service 는 분기 X.
 */
@Service
@RequiredArgsConstructor
public class RequestSentenceFeedbackService {

    private static final Duration TTL = Duration.ofHours(24);

    private final SentenceFeedbackRepository repository;
    private final SentenceFeedbackAiGateway aiGateway;
    private final OutboxEventPublisher outboxEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public SentenceFeedbackResult request(RequestSentenceFeedbackCommand command) {
        SentenceText sentence = new SentenceText(command.sentence());
        Tone tone = parseTone(command.toneOrNull());

        SentenceFeedbackId feedbackId = SentenceFeedbackId.newId();
        Instant requestedAt = Instant.now(clock);

        // T1 — REQUESTED 영속 + Outbox 발행 (§12 — request 트랜잭션)
        transactionTemplate.executeWithoutResult(status -> {
            SentenceFeedback fb = SentenceFeedback.request(
                feedbackId, command.userId(), command.diaryIdOrNull(),
                sentence, tone, requestedAt
            );
            repository.save(fb);
            outboxEventPublisher.publish(new SentenceFeedbackRequested(
                UUID.randomUUID().toString(),    // eventId 멱등성 키
                requestedAt,
                feedbackId.asString(),
                command.userId().toString()
            ));
        });

        // 트랜잭션 외부 — chat-service 호출 (Deadline 35s, AiGateway Adapter 책임)
        SentenceFeedbackAiGateway.Result aiResult = aiGateway.request(new SentenceFeedbackAiGateway.Args(
            command.userId(),
            sentence,
            command.priorSentences(),
            tone,
            feedbackId.asString()  // requestId = feedbackId (chat-service 측 분산 trace)
        ));

        // T2 — status 전이 (markSuggested / markFailed)
        SentenceFeedback finalState = transactionTemplate.execute(status -> {
            SentenceFeedback fb = repository.findById(feedbackId)
                .orElseThrow(() -> new SentenceFeedbackNotFoundException(
                    "feedback disappeared between transactions: " + feedbackId.asString()
                ));
            if (aiResult.status() == SentenceFeedbackAiGateway.Result.Status.SUGGESTED) {
                Instant expiresAt = Instant.now(clock).plus(TTL);
                fb.markSuggested(aiResult.suggestions(), expiresAt, clock);
            } else {
                String reason = aiResult.failureReasonOrNull() != null
                    ? aiResult.failureReasonOrNull()
                    : "ai gateway returned FAILED";
                fb.markFailed(reason, clock);
            }
            repository.save(fb);
            return fb;
        });

        return SentenceFeedbackResult.from(finalState, Instant.now(clock));
    }

    /**
     * 클라 입력 문자열 → Tone enum. null/blank/unknown → null (chat-service default 적용).
     * §10 — "casual" / "formal" / "neutral" 화이트리스트.
     */
    private static Tone parseTone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "casual" -> Tone.CASUAL;
            case "formal" -> Tone.FORMAL;
            case "neutral" -> Tone.NEUTRAL;
            default -> null;  // unknown → chat-service default (§10 forward 호환)
        };
    }
}
