package app.backend.jamo.diary.infrastructure.grpc.client;

import app.backend.jamo.contracts.proto.chat.AiAssistantServiceGrpc;
import app.backend.jamo.contracts.proto.chat.ChatMessage;
import app.backend.jamo.contracts.proto.chat.ChatResponseReply;
import app.backend.jamo.contracts.proto.chat.ChatResponseRequest;
import app.backend.jamo.diary.domain.repository.DiaryChatAiGateway;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * {@link DiaryChatAiGateway} 의 gRPC 어댑터 — chat-service AiAssistantService.GenerateChatResponse 호출 (S4).
 *
 * <p>박제: docs/decisions/diary/diarychat-domain-policy-v2-apispec-e.md §"AI 자동응답" + ADR-0003.
 * 적용 정책 (CLAUDE.md NEVER): Deadline(기본 35s = chat LLM 30s + 마진), Circuit Breaker, Retry.
 * 전용 인스턴스 {@code chatServiceGenerate} — GenerateChatResponse 는 비멱등(LLM 비용 + chat-service
 * rate-limit 카운터 증가)이라 ADR-0003 "다른 서비스 → chat: Retry 0" 정책에 맞춰 retry 0 (code-reviewer H1).
 * gRPC 시스템 오류 / Circuit OPEN / 응답 status="FAILED" 를 모두
 * {@link DiaryChatAiGateway.Result#failed()} 로 일원화 (호출 측 분기 단순화).
 */
@Component
@Slf4j
public class ChatServiceDiaryChatGatewayAdapter implements DiaryChatAiGateway {

    private static final String STATUS_OK = "OK";
    private static final String STATUS_RATE_LIMITED = "RATE_LIMITED";

    private final AiAssistantServiceGrpc.AiAssistantServiceBlockingStub stub;
    private final long deadlineMillis;

    public ChatServiceDiaryChatGatewayAdapter(
        @GrpcClient("chat-service") AiAssistantServiceGrpc.AiAssistantServiceBlockingStub stub,
        @Value("${jamo.diarychat.ai-deadline:PT35S}") Duration deadline
    ) {
        this.stub = stub;
        this.deadlineMillis = deadline.toMillis();
    }

    @Override
    @CircuitBreaker(name = "chatServiceGenerate", fallbackMethod = "fallback")
    @Retry(name = "chatServiceGenerate")
    public Result generate(Args args) {
        try {
            ChatResponseRequest.Builder req = ChatResponseRequest.newBuilder()
                .setUserId(args.userId().toString())
                .setRoomId(Long.toString(args.roomId()))
                .setUserMessage(args.userMessage())
                .setRequestId(args.requestId());
            for (RecentMessage m : args.recentMessages()) {
                req.addRecentMessages(ChatMessage.newBuilder()
                    .setAuthorRole(m.role() == null ? "" : m.role())
                    .setText(m.text() == null ? "" : m.text())
                    .build());
            }

            ChatResponseReply reply = stub
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .generateChatResponse(req.build());

            return mapReply(reply);
        } catch (StatusRuntimeException ex) {
            log.warn("chat-service GenerateChatResponse gRPC failed: roomId={} status={} msg={}",
                args.roomId(), ex.getStatus().getCode(), ex.getStatus().getDescription());
            throw ex;  // @Retry 트리거 — 모든 시도 실패 후 fallback
        }
    }

    /** Resilience4j fallback — Circuit OPEN / retry 소진. FAILED 일원화 (사용자 흐름은 SYSTEM 메시지로 보호). */
    @SuppressWarnings("unused")
    private Result fallback(Args args, Throwable ex) {
        log.warn("chat-service GenerateChatResponse fallback: roomId={} cause={}",
            args.roomId(), ex.getClass().getSimpleName());
        return Result.failed();
    }

    private Result mapReply(ChatResponseReply reply) {
        String status = reply.getStatus();
        if (STATUS_OK.equals(status)) {
            String text = reply.getAssistantMessage();
            if (text == null || text.isBlank()) {
                log.warn("chat-service returned OK with blank assistant_message: requestId={}",
                    reply.getRequestId());
                return Result.failed();
            }
            return Result.ok(text);
        }
        if (STATUS_RATE_LIMITED.equals(status)) {
            return Result.rateLimited();
        }
        // FAILED / 알 수 없는 status — 모두 FAILED 일원화.
        return Result.failed();
    }
}
