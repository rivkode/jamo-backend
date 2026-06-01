package app.backend.jamo.diary.infrastructure.grpc;

import app.backend.jamo.contracts.proto.chat.AiAssistantServiceGrpc;
import app.backend.jamo.contracts.proto.chat.ChatResponseReply;
import app.backend.jamo.contracts.proto.chat.ChatResponseRequest;
import app.backend.jamo.diary.domain.repository.DiaryChatAiGateway;
import app.backend.jamo.diary.infrastructure.grpc.client.ChatServiceDiaryChatGatewayAdapter;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ChatServiceDiaryChatGatewayAdapter 단위 테스트 — InProcess gRPC server + 자체 stub (S4).
 *
 * <p>Resilience4j(@Retry/@CircuitBreaker)는 Spring AOP 라 단위 테스트(no Spring context)에서는 비활성.
 * 본 테스트는 매핑(status→Result) / 요청 필드 직렬화 / gRPC 시스템 오류 → throw 동작만 검증.
 */
class ChatServiceDiaryChatGatewayAdapterTest {

    private Server server;
    private ManagedChannel channel;
    private final AtomicReference<Function<ChatResponseRequest, ChatResponseReply>> handler =
        new AtomicReference<>();
    private final AtomicReference<Status> errorStatus = new AtomicReference<>();
    private final AtomicReference<ChatResponseRequest> lastReceived = new AtomicReference<>();

    private ChatServiceDiaryChatGatewayAdapter adapter;

    @BeforeEach
    void setup() throws IOException {
        String name = "test-" + UUID.randomUUID();
        server = InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(new AiAssistantServiceGrpc.AiAssistantServiceImplBase() {
                @Override
                public void generateChatResponse(ChatResponseRequest request,
                                                 StreamObserver<ChatResponseReply> obs) {
                    lastReceived.set(request);
                    Status err = errorStatus.get();
                    if (err != null) {
                        obs.onError(new StatusRuntimeException(err));
                        return;
                    }
                    obs.onNext(handler.get().apply(request));
                    obs.onCompleted();
                }
            })
            .build()
            .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();

        AiAssistantServiceGrpc.AiAssistantServiceBlockingStub stub =
            AiAssistantServiceGrpc.newBlockingStub(channel);
        adapter = new ChatServiceDiaryChatGatewayAdapter(stub, Duration.ofSeconds(5));
    }

    @AfterEach
    void teardown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    void OK_reply_maps_to_Result_ok() {
        handler.set(req -> ChatResponseReply.newBuilder()
            .setStatus("OK").setAssistantMessage("좋은 하루였네요!").setRequestId(req.getRequestId()).build());

        DiaryChatAiGateway.Result result = adapter.generate(args());

        assertThat(result.status()).isEqualTo(DiaryChatAiGateway.Status.OK);
        assertThat(result.assistantMessage()).isEqualTo("좋은 하루였네요!");
    }

    @Test
    void OK_with_blank_message_maps_to_FAILED() {
        handler.set(req -> ChatResponseReply.newBuilder()
            .setStatus("OK").setAssistantMessage("  ").build());

        assertThat(adapter.generate(args()).status()).isEqualTo(DiaryChatAiGateway.Status.FAILED);
    }

    @Test
    void FAILED_reply_maps_to_FAILED() {
        handler.set(req -> ChatResponseReply.newBuilder().setStatus("FAILED").build());

        assertThat(adapter.generate(args()).status()).isEqualTo(DiaryChatAiGateway.Status.FAILED);
    }

    @Test
    void RATE_LIMITED_reply_maps_to_rateLimited() {
        handler.set(req -> ChatResponseReply.newBuilder().setStatus("RATE_LIMITED").build());

        assertThat(adapter.generate(args()).status()).isEqualTo(DiaryChatAiGateway.Status.RATE_LIMITED);
    }

    @Test
    void unknown_status_maps_to_FAILED() {
        handler.set(req -> ChatResponseReply.newBuilder().setStatus("WHATEVER").build());

        assertThat(adapter.generate(args()).status()).isEqualTo(DiaryChatAiGateway.Status.FAILED);
    }

    @Test
    void grpc_UNAVAILABLE_throws_for_retry_aspect() {
        errorStatus.set(Status.UNAVAILABLE);
        assertThatThrownBy(() -> adapter.generate(args()))
            .isInstanceOf(StatusRuntimeException.class)
            .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                .isEqualTo(Status.Code.UNAVAILABLE));
    }

    @Test
    void request_fields_sent_to_chat_service() {
        handler.set(req -> ChatResponseReply.newBuilder().setStatus("FAILED").build());
        UUID userId = UUID.randomUUID();
        DiaryChatAiGateway.Args args = new DiaryChatAiGateway.Args(
            userId, 7L, "현재 메시지",
            List.of(new DiaryChatAiGateway.RecentMessage("user", "이전"),
                new DiaryChatAiGateway.RecentMessage("assistant", "이전 응답")),
            "rid-9");

        adapter.generate(args);

        ChatResponseRequest sent = lastReceived.get();
        assertThat(sent.getUserId()).isEqualTo(userId.toString());
        assertThat(sent.getRoomId()).isEqualTo("7");
        assertThat(sent.getUserMessage()).isEqualTo("현재 메시지");
        assertThat(sent.getRequestId()).isEqualTo("rid-9");
        assertThat(sent.getRecentMessagesList()).hasSize(2);
        assertThat(sent.getRecentMessages(0).getAuthorRole()).isEqualTo("user");
        assertThat(sent.getRecentMessages(0).getText()).isEqualTo("이전");
        assertThat(sent.getRecentMessages(1).getAuthorRole()).isEqualTo("assistant");
    }

    private DiaryChatAiGateway.Args args() {
        return new DiaryChatAiGateway.Args(
            UUID.randomUUID(), 1L, "안녕", List.of(), UUID.randomUUID().toString());
    }
}
