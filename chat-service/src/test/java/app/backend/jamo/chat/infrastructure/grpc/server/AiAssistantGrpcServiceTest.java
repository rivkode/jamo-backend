package app.backend.jamo.chat.infrastructure.grpc.server;

import app.backend.jamo.chat.application.dto.GenerateChatResponseCommand;
import app.backend.jamo.chat.application.dto.GenerateChatResponseResult;
import app.backend.jamo.chat.application.service.GenerateChatResponseService;
import app.backend.jamo.contracts.proto.chat.ChatMessage;
import app.backend.jamo.contracts.proto.chat.ChatResponseReply;
import app.backend.jamo.contracts.proto.chat.ChatResponseRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAssistantGrpcServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private GenerateChatResponseService generateService;
    private AiAssistantGrpcService grpcService;

    @BeforeEach
    void setUp() {
        generateService = mock(GenerateChatResponseService.class);
        grpcService = new AiAssistantGrpcService(generateService, CLOCK);
    }

    /** onNext/onError 를 수집하는 StreamObserver. */
    private static final class CapturingObserver implements StreamObserver<ChatResponseReply> {
        ChatResponseReply reply;
        Throwable error;
        @Override public void onNext(ChatResponseReply value) { this.reply = value; }
        @Override public void onError(Throwable t) { this.error = t; }
        @Override public void onCompleted() { }
    }

    private ChatResponseRequest request(String userId, String userMessage, String requestId) {
        return ChatResponseRequest.newBuilder()
            .setUserId(userId).setRoomId("1").setUserMessage(userMessage).setRequestId(requestId).build();
    }

    @Test
    void ok_result_mapped_to_reply() {
        when(generateService.generate(any())).thenReturn(GenerateChatResponseResult.ok("좋은 하루였네요!"));
        CapturingObserver obs = new CapturingObserver();

        grpcService.generateChatResponse(request("u1", "안녕", "rid-1"), obs);

        assertThat(obs.error).isNull();
        assertThat(obs.reply.getStatus()).isEqualTo("OK");
        assertThat(obs.reply.getAssistantMessage()).isEqualTo("좋은 하루였네요!");
        assertThat(obs.reply.getGeneratedAtEpochMs()).isEqualTo(NOW.toEpochMilli());
        assertThat(obs.reply.getRequestId()).isEqualTo("rid-1");
    }

    @Test
    void failed_result_mapped_to_FAILED_status_not_grpc_error() {
        when(generateService.generate(any())).thenReturn(GenerateChatResponseResult.failed());
        CapturingObserver obs = new CapturingObserver();

        grpcService.generateChatResponse(request("u1", "안녕", "rid-1"), obs);

        assertThat(obs.error).isNull();  // body status 로 전달 — gRPC error 아님
        assertThat(obs.reply.getStatus()).isEqualTo("FAILED");
        assertThat(obs.reply.getAssistantMessage()).isEmpty();  // proto string null 불가 (test-reviewer M3)
    }

    @Test
    void rate_limited_result_mapped_to_RATE_LIMITED_status() {
        when(generateService.generate(any())).thenReturn(GenerateChatResponseResult.rateLimited());
        CapturingObserver obs = new CapturingObserver();

        grpcService.generateChatResponse(request("u1", "안녕", "rid-1"), obs);

        assertThat(obs.reply.getStatus()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void blank_user_message_rejected_with_INVALID_ARGUMENT() {
        CapturingObserver obs = new CapturingObserver();

        grpcService.generateChatResponse(request("u1", "   ", "rid-1"), obs);

        assertThat(obs.reply).isNull();
        assertThat(((StatusRuntimeException) obs.error).getStatus().getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void blank_user_id_rejected_with_INVALID_ARGUMENT() {
        CapturingObserver obs = new CapturingObserver();

        grpcService.generateChatResponse(request("", "안녕", "rid-1"), obs);

        assertThat(((StatusRuntimeException) obs.error).getStatus().getCode())
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void empty_request_id_is_generated_in_reply() {
        when(generateService.generate(any())).thenReturn(GenerateChatResponseResult.ok("ok"));
        CapturingObserver obs = new CapturingObserver();

        grpcService.generateChatResponse(request("u1", "안녕", ""), obs);

        assertThat(obs.reply.getRequestId()).isNotBlank();
    }

    @Test
    void recent_messages_and_fields_forwarded_to_service() {
        when(generateService.generate(any())).thenReturn(GenerateChatResponseResult.ok("ok"));
        List<ChatMessage> recent = new ArrayList<>();
        recent.add(ChatMessage.newBuilder().setAuthorRole("user").setText("이전").build());
        ChatResponseRequest req = ChatResponseRequest.newBuilder()
            .setUserId("u9").setRoomId("5").setUserMessage("현재").setRequestId("r")
            .addAllRecentMessages(recent).build();

        grpcService.generateChatResponse(req, new CapturingObserver());

        ArgumentCaptor<GenerateChatResponseCommand> captor =
            ArgumentCaptor.forClass(GenerateChatResponseCommand.class);
        verify(generateService).generate(captor.capture());
        GenerateChatResponseCommand cmd = captor.getValue();
        assertThat(cmd.userId()).isEqualTo("u9");
        assertThat(cmd.roomId()).isEqualTo("5");
        assertThat(cmd.userMessage()).isEqualTo("현재");
        assertThat(cmd.recentMessages()).hasSize(1);
        assertThat(cmd.recentMessages().get(0).role()).isEqualTo("user");
        assertThat(cmd.recentMessages().get(0).text()).isEqualTo("이전");
    }
}
