package app.backend.jamo.diary.infrastructure.grpc.client;

import app.backend.jamo.contracts.proto.chat.AiAssistantServiceGrpc;
import app.backend.jamo.contracts.proto.chat.SentenceFeedbackRequest;
import app.backend.jamo.contracts.proto.chat.SentenceFeedbackResponse;
import app.backend.jamo.contracts.proto.chat.SentenceSuggestion;
import app.backend.jamo.diary.domain.model.sentencefeedback.Suggestion;
import app.backend.jamo.diary.domain.model.sentencefeedback.SuggestionId;
import app.backend.jamo.diary.domain.model.sentencefeedback.Tone;
import app.backend.jamo.diary.domain.repository.SentenceFeedbackAiGateway;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * SentenceFeedbackAiGateway 의 gRPC 어댑터 구현 (chat-service AiAssistantService.RequestSentenceFeedback).
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §6 / §7 / §10 +
 * decisions/contracts/ai-assistant-service-method-catalog.md §28 (Deadline 35s).
 *
 * <p>적용 정책 (CLAUDE.md NEVER):
 * <ul>
 *   <li><b>Deadline 35s</b> — {@code stub.withDeadlineAfter(35, SECONDS)}</li>
 *   <li><b>Circuit Breaker</b> — {@code @CircuitBreaker(name="chatService", fallbackMethod="fallback")}</li>
 *   <li><b>Retry</b> — {@code @Retry(name="chatService")} (gRPC StatusRuntimeException 만)</li>
 *   <li><b>FAILED 일원화</b> — gRPC 시스템 오류 / Circuit OPEN / 응답 status="FAILED" 모두
 *       {@link SentenceFeedbackAiGateway.Result#failed} 로 변환 (Application Service 분기 단순화)</li>
 * </ul>
 *
 * <p>Tone wire format 변환: enum {@link Tone} → lowercase string ("casual"/"formal"/"neutral"). null →
 * 빈 문자열 (chat-service default 적용 §10).
 */
@Component
@Slf4j
public class ChatServiceSentenceFeedbackGatewayAdapter implements SentenceFeedbackAiGateway {

    private static final String STATUS_SUGGESTED = "SUGGESTED";
    private static final String STATUS_FAILED = "FAILED";

    private final AiAssistantServiceGrpc.AiAssistantServiceBlockingStub stub;
    private final long deadlineMillis;

    public ChatServiceSentenceFeedbackGatewayAdapter(
        @GrpcClient("chat-service") AiAssistantServiceGrpc.AiAssistantServiceBlockingStub stub,
        @Value("${jamo.sentence-feedback.chat-service-deadline:PT35S}") Duration deadline
    ) {
        this.stub = stub;
        this.deadlineMillis = deadline.toMillis();
    }

    @Override
    @CircuitBreaker(name = "chatService", fallbackMethod = "fallback")
    @Retry(name = "chatService")
    public Result request(Args args) {
        try {
            SentenceFeedbackRequest request = SentenceFeedbackRequest.newBuilder()
                .setUserId(args.userId().toString())
                .setSentence(args.sentence().value())
                .addAllPriorSentences(args.priorSentences())
                .setTone(toWireTone(args.toneOrNull()))
                .setRequestId(args.requestId())
                .build();

            SentenceFeedbackResponse response = stub
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .requestSentenceFeedback(request);

            return mapResponse(response);
        } catch (StatusRuntimeException ex) {
            log.warn("chat-service RequestSentenceFeedback gRPC failed: status={} msg={}",
                ex.getStatus().getCode(), ex.getStatus().getDescription());
            throw ex;  // @Retry 트리거 — 모든 시도 실패 후 fallback 호출
        }
    }

    /**
     * Resilience4j fallback — Circuit OPEN / 모든 retry 실패 후 호출. FAILED Result 일원화. failureReason
     * 은 sanitized 식별자 (security-reviewer M-4 — exception class / 내부 메시지 영속 회피).
     */
    @SuppressWarnings("unused")
    private Result fallback(Args args, Throwable ex) {
        log.warn("chat-service RequestSentenceFeedback fallback feedbackId={} cause={}",
            args.requestId(), ex.getClass().getSimpleName());
        return Result.failed("CHAT_UNAVAILABLE");
    }

    private Result mapResponse(SentenceFeedbackResponse response) {
        String status = response.getStatus();
        if (STATUS_SUGGESTED.equals(status)) {
            try {
                List<Suggestion> suggestions = response.getSuggestionsList().stream()
                    .map(ChatServiceSentenceFeedbackGatewayAdapter::toDomainSuggestion)
                    .toList();
                if (suggestions.isEmpty()) {
                    log.warn("chat-service returned SUGGESTED with empty suggestions: requestId={}",
                        response.getRequestId());
                    return Result.failed("CHAT_INVALID_RESPONSE: empty suggestions");
                }
                return Result.suggested(suggestions);
            } catch (IllegalArgumentException ex) {
                // suggestionId 가 invalid UUID / Suggestion VO invariant 위반 (chat-service 발행 데이터 오류)
                // — Application 까지 propagate 시 사용자 흐름 차단. FAILED 일원화 + 운영 로그 (security-reviewer M-4).
                log.warn("chat-service returned malformed suggestion: requestId={} cause={}",
                    response.getRequestId(), ex.getMessage());
                return Result.failed("CHAT_INVALID_RESPONSE: malformed suggestion");
            }
        }
        if (STATUS_FAILED.equals(status)) {
            return Result.failed("CHAT_FAILED");
        }
        // 알 수 없는 status (chat-service 정책 미정합 또는 빈 status) — sanitized error code 일원화
        // (security-reviewer M-4 — chat-service 의 자유 텍스트 status 가 그대로 영속 / 노출되지 않도록)
        log.warn("chat-service returned unknown status: requestId={} status='{}'",
            response.getRequestId(), status);
        return Result.failed("CHAT_UNKNOWN_STATUS");
    }

    private static Suggestion toDomainSuggestion(SentenceSuggestion proto) {
        UUID id = UUID.fromString(proto.getSuggestionId());  // invalid → IAE → mapResponse catch 로 FAILED 일원화
        return new Suggestion(SuggestionId.of(id), proto.getText(), proto.getReason(), proto.getConfidence());
    }

    private static String toWireTone(Tone tone) {
        return tone == null ? "" : tone.name().toLowerCase(Locale.ROOT);
    }
}
