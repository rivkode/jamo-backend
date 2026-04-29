package app.backend.jamo.diary.infrastructure.grpc;

import app.backend.jamo.contracts.proto.chat.AiAssistantServiceGrpc;
import app.backend.jamo.contracts.proto.chat.SentenceFeedbackRequest;
import app.backend.jamo.contracts.proto.chat.SentenceFeedbackResponse;
import app.backend.jamo.contracts.proto.chat.SentenceSuggestion;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceText;
import app.backend.jamo.diary.domain.model.sentencefeedback.Tone;
import app.backend.jamo.diary.domain.repository.SentenceFeedbackAiGateway;
import app.backend.jamo.diary.infrastructure.grpc.client.ChatServiceSentenceFeedbackGatewayAdapter;
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
 * ChatServiceSentenceFeedbackGatewayAdapter 단위 테스트 — InProcess gRPC server + 자체 stub.
 *
 * <p>Resilience4j (@Retry / @CircuitBreaker) 는 Spring AOP 라 단위 테스트 (no Spring context) 에서는
 * <b>비활성</b>. 본 테스트는 Adapter 의 매핑 / Tone wire format / 시스템 오류 → throw 동작만 검증.
 * Resilience4j fallback 자체는 별도 통합 테스트 (운영 검증) 영역.
 */
class ChatServiceSentenceFeedbackGatewayAdapterTest {

    private Server server;
    private ManagedChannel channel;
    private final AtomicReference<Function<SentenceFeedbackRequest, SentenceFeedbackResponse>> handler =
        new AtomicReference<>();
    private final AtomicReference<Status> errorStatus = new AtomicReference<>();
    private final AtomicReference<SentenceFeedbackRequest> lastReceived = new AtomicReference<>();

    private ChatServiceSentenceFeedbackGatewayAdapter adapter;

    @BeforeEach
    void setup() throws IOException {
        String name = "test-" + UUID.randomUUID();
        server = InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(new AiAssistantServiceGrpc.AiAssistantServiceImplBase() {
                @Override
                public void requestSentenceFeedback(SentenceFeedbackRequest request,
                                                    StreamObserver<SentenceFeedbackResponse> obs) {
                    lastReceived.set(request);
                    Status err = errorStatus.get();
                    if (err != null) {
                        obs.onError(new StatusRuntimeException(err));
                        return;
                    }
                    Function<SentenceFeedbackRequest, SentenceFeedbackResponse> h = handler.get();
                    obs.onNext(h.apply(request));
                    obs.onCompleted();
                }
            })
            .build()
            .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();

        AiAssistantServiceGrpc.AiAssistantServiceBlockingStub stub =
            AiAssistantServiceGrpc.newBlockingStub(channel);
        adapter = new ChatServiceSentenceFeedbackGatewayAdapter(stub, Duration.ofSeconds(5));
    }

    @AfterEach
    void teardown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    void SUGGESTED_response_maps_to_Result_suggested() {
        UUID suggestionId = UUID.randomUUID();
        handler.set(req -> SentenceFeedbackResponse.newBuilder()
            .setStatus("SUGGESTED")
            .addSuggestions(SentenceSuggestion.newBuilder()
                .setSuggestionId(suggestionId.toString())
                .setText("맑은 하루였다")
                .setReason("간결한 표현")
                .setConfidence(0.85)
                .build())
            .setRequestId(req.getRequestId())
            .build());

        SentenceFeedbackAiGateway.Result result = adapter.request(args(Tone.CASUAL));

        assertThat(result.status()).isEqualTo(SentenceFeedbackAiGateway.Result.Status.SUGGESTED);
        assertThat(result.suggestions()).hasSize(1);
        assertThat(result.suggestions().get(0).id().value()).isEqualTo(suggestionId);
        assertThat(result.suggestions().get(0).text()).isEqualTo("맑은 하루였다");
        assertThat(result.failureReasonOrNull()).isNull();
    }

    @Test
    void FAILED_response_maps_to_sanitized_CHAT_FAILED() {
        handler.set(req -> SentenceFeedbackResponse.newBuilder()
            .setStatus("FAILED")
            .setRequestId(req.getRequestId())
            .build());

        SentenceFeedbackAiGateway.Result result = adapter.request(args(null));

        assertThat(result.status()).isEqualTo(SentenceFeedbackAiGateway.Result.Status.FAILED);
        assertThat(result.suggestions()).isEmpty();
        // security-reviewer M-4 — sanitized error code (chat-service 자유 텍스트 미노출)
        assertThat(result.failureReasonOrNull()).isEqualTo("CHAT_FAILED");
    }

    @Test
    void SUGGESTED_with_empty_suggestions_falls_back_to_CHAT_INVALID_RESPONSE() {
        handler.set(req -> SentenceFeedbackResponse.newBuilder()
            .setStatus("SUGGESTED")
            .setRequestId(req.getRequestId())
            .build());

        SentenceFeedbackAiGateway.Result result = adapter.request(args(null));

        assertThat(result.status()).isEqualTo(SentenceFeedbackAiGateway.Result.Status.FAILED);
        assertThat(result.failureReasonOrNull()).isEqualTo("CHAT_INVALID_RESPONSE: empty suggestions");
    }

    @Test
    void SUGGESTED_with_invalid_suggestionId_falls_back_to_CHAT_INVALID_RESPONSE() {
        // chat-service 가 invalid UUID 발행 시 IAE → fallback FAILED 일원화 (security-reviewer N3, M-4).
        // Application Service 까지 propagate 되어 사용자 흐름이 차단되지 않도록.
        handler.set(req -> SentenceFeedbackResponse.newBuilder()
            .setStatus("SUGGESTED")
            .addSuggestions(SentenceSuggestion.newBuilder()
                .setSuggestionId("not-a-uuid")
                .setText("hello").setReason("ok").setConfidence(0.5)
                .build())
            .build());

        SentenceFeedbackAiGateway.Result result = adapter.request(args(null));

        assertThat(result.status()).isEqualTo(SentenceFeedbackAiGateway.Result.Status.FAILED);
        assertThat(result.failureReasonOrNull()).isEqualTo("CHAT_INVALID_RESPONSE: malformed suggestion");
    }

    @Test
    void unknown_status_falls_back_to_CHAT_UNKNOWN_STATUS() {
        handler.set(req -> SentenceFeedbackResponse.newBuilder()
            .setStatus("WHATEVER")
            .setRequestId(req.getRequestId())
            .build());

        SentenceFeedbackAiGateway.Result result = adapter.request(args(null));

        assertThat(result.status()).isEqualTo(SentenceFeedbackAiGateway.Result.Status.FAILED);
        assertThat(result.failureReasonOrNull()).isEqualTo("CHAT_UNKNOWN_STATUS");
    }

    @Test
    void empty_status_falls_back_to_CHAT_UNKNOWN_STATUS() {
        handler.set(req -> SentenceFeedbackResponse.newBuilder().build());

        SentenceFeedbackAiGateway.Result result = adapter.request(args(null));

        assertThat(result.status()).isEqualTo(SentenceFeedbackAiGateway.Result.Status.FAILED);
        assertThat(result.failureReasonOrNull()).isEqualTo("CHAT_UNKNOWN_STATUS");
    }

    @Test
    void DEADLINE_EXCEEDED_throws_StatusRuntimeException_for_retry_aspect() {
        // 단위 테스트는 Resilience4j AOP 비활성 → exception 그대로 노출 (Spring 통합 시 fallback 호출).
        // test-reviewer H3 — status code 까지 명시 단언.
        errorStatus.set(Status.DEADLINE_EXCEEDED);
        assertThatThrownBy(() -> adapter.request(args(null)))
            .isInstanceOf(StatusRuntimeException.class)
            .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                .isEqualTo(Status.Code.DEADLINE_EXCEEDED));
    }

    @Test
    void UNAVAILABLE_throws_StatusRuntimeException_for_retry_aspect() {
        errorStatus.set(Status.UNAVAILABLE);
        assertThatThrownBy(() -> adapter.request(args(null)))
            .isInstanceOf(StatusRuntimeException.class)
            .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                .isEqualTo(Status.Code.UNAVAILABLE));
    }

    @Test
    void Tone_CASUAL_serialized_as_lowercase_string() {
        handler.set(req -> SentenceFeedbackResponse.newBuilder().setStatus("FAILED").build());
        adapter.request(args(Tone.CASUAL));
        assertThat(lastReceived.get().getTone()).isEqualTo("casual");
    }

    @Test
    void Tone_FORMAL_serialized_as_lowercase_string() {
        handler.set(req -> SentenceFeedbackResponse.newBuilder().setStatus("FAILED").build());
        adapter.request(args(Tone.FORMAL));
        assertThat(lastReceived.get().getTone()).isEqualTo("formal");
    }

    @Test
    void Tone_NEUTRAL_serialized_as_lowercase_string() {
        handler.set(req -> SentenceFeedbackResponse.newBuilder().setStatus("FAILED").build());
        adapter.request(args(Tone.NEUTRAL));
        assertThat(lastReceived.get().getTone()).isEqualTo("neutral");
    }

    @Test
    void Tone_null_serialized_as_empty_string_for_chat_service_default() {
        handler.set(req -> SentenceFeedbackResponse.newBuilder().setStatus("FAILED").build());
        adapter.request(args(null));
        assertThat(lastReceived.get().getTone()).isEmpty();
    }

    @Test
    void requestId_priorSentences_userId_sent_to_chat_service() {
        handler.set(req -> SentenceFeedbackResponse.newBuilder().setStatus("FAILED").build());
        UUID userId = UUID.randomUUID();
        SentenceFeedbackAiGateway.Args args = new SentenceFeedbackAiGateway.Args(
            userId, new SentenceText("hello"),
            List.of("prev1", "prev2"), null,
            "rid-123"
        );

        adapter.request(args);

        SentenceFeedbackRequest sent = lastReceived.get();
        assertThat(sent.getUserId()).isEqualTo(userId.toString());
        assertThat(sent.getSentence()).isEqualTo("hello");
        assertThat(sent.getPriorSentencesList()).containsExactly("prev1", "prev2");
        assertThat(sent.getRequestId()).isEqualTo("rid-123");
    }

    private SentenceFeedbackAiGateway.Args args(Tone tone) {
        return new SentenceFeedbackAiGateway.Args(
            UUID.randomUUID(),
            new SentenceText("oneline"),
            List.of(), tone,
            UUID.randomUUID().toString()
        );
    }
}
