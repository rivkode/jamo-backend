package app.backend.jamo.chat.infrastructure.grpc.server;

import app.backend.jamo.chat.application.dto.GenerateChatResponseCommand;
import app.backend.jamo.chat.application.dto.GenerateChatResponseCommand.RecentMessage;
import app.backend.jamo.chat.application.dto.GenerateChatResponseResult;
import app.backend.jamo.chat.application.service.GenerateChatResponseService;
import app.backend.jamo.contracts.proto.chat.AiAssistantServiceGrpc;
import app.backend.jamo.contracts.proto.chat.ChatMessage;
import app.backend.jamo.contracts.proto.chat.ChatResponseReply;
import app.backend.jamo.contracts.proto.chat.ChatResponseRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * {@code AiAssistantService.GenerateChatResponse} gRPC server — diarychat AI 자동응답 (S4, ADR-0003).
 *
 * <p>호출자: diary-service (사용자가 ai-enabled 방에 메시지를 보내면 비동기 호출). chat-service 가 프롬프트
 * 합성 / rate limit / fallback 정책을 처리한 뒤 내부적으로 ai-service AiService.Complete(LLM) 호출.
 *
 * <p><b>응답 정책</b> (DiaryQueryGrpcService body-status 패턴 정합):
 * <ul>
 *   <li>정상 → status="OK" + assistant_message.</li>
 *   <li>AI 장애 / 부적합 응답 → status="FAILED" (gRPC 200). diary 가 SYSTEM 안내 메시지로 매핑.</li>
 *   <li>사용량 한도 → status="RATE_LIMITED" (gRPC 200).</li>
 *   <li>user_message / user_id 누락 → gRPC {@code INVALID_ARGUMENT} (호출 측 프로그래밍 오류).</li>
 * </ul>
 * 예외를 gRPC error 로 던지지 않아 diary 의 retry/circuit 오발동을 막고, 실패 UX 를 body status 로 단순화.
 */
@GrpcService
@Slf4j
public class AiAssistantGrpcService extends AiAssistantServiceGrpc.AiAssistantServiceImplBase {

    private final GenerateChatResponseService generateService;
    private final Clock clock;

    public AiAssistantGrpcService(GenerateChatResponseService generateService, Clock clock) {
        this.generateService = generateService;
        this.clock = clock;
    }

    @Override
    public void generateChatResponse(ChatResponseRequest request,
                                     StreamObserver<ChatResponseReply> responseObserver) {
        if (request.getUserMessage().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription("user_message must not be blank").asRuntimeException());
            return;
        }
        if (request.getUserId().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription("user_id must not be blank").asRuntimeException());
            return;
        }

        String requestId = request.getRequestId().isBlank()
            ? UUID.randomUUID().toString()
            : request.getRequestId();

        GenerateChatResponseResult result = generateService.generate(new GenerateChatResponseCommand(
            request.getUserId(),
            request.getRoomId(),
            request.getUserMessage(),
            toRecentMessages(request.getRecentMessagesList())));

        responseObserver.onNext(ChatResponseReply.newBuilder()
            .setAssistantMessage(result.assistantMessage())
            .setStatus(result.status().name())
            .setGeneratedAtEpochMs(clock.millis())
            .setRequestId(requestId)
            .build());
        responseObserver.onCompleted();
    }

    private static List<RecentMessage> toRecentMessages(List<ChatMessage> protos) {
        return protos.stream()
            .map(p -> new RecentMessage(p.getAuthorRole(), p.getText()))
            .toList();
    }
}
