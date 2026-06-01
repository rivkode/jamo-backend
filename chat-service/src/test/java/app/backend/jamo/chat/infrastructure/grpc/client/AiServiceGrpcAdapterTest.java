package app.backend.jamo.chat.infrastructure.grpc.client;

import app.backend.jamo.chat.domain.ai.AiTimeoutException;
import app.backend.jamo.chat.domain.ai.AiUnavailableException;
import app.backend.jamo.chat.domain.ai.SynthesizedSpeech;
import app.backend.jamo.chat.domain.ai.TranscriptResult;
import app.backend.jamo.contracts.proto.ai.AiServiceGrpc;
import app.backend.jamo.contracts.proto.ai.SpeechToTextRequest;
import app.backend.jamo.contracts.proto.ai.SpeechToTextResponse;
import app.backend.jamo.contracts.proto.ai.TextToSpeechRequest;
import app.backend.jamo.contracts.proto.ai.TextToSpeechResponse;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AiServiceGrpcAdapter ↔ ai-service AiService gRPC 매핑 검증 — InProcess transport + fake servicer.
 * Resilience4j 어노테이션은 Spring AOP 런타임 전용이라 본 단위 테스트엔 비활성(catch→AiUnavailableException 경로 검증).
 */
class AiServiceGrpcAdapterTest {

    private Server server;
    private ManagedChannel channel;
    private AiServiceGrpcAdapter adapter;
    private final AtomicReference<SpeechToTextRequest> lastStt = new AtomicReference<>();
    private final AtomicReference<TextToSpeechRequest> lastTts = new AtomicReference<>();
    private Status failStatus;  // null = 정상, 그 외 = 해당 status 로 onError

    @BeforeEach
    void setUp() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
            .addService(new AiServiceGrpc.AiServiceImplBase() {
                @Override
                public void speechToText(SpeechToTextRequest request,
                                         StreamObserver<SpeechToTextResponse> obs) {
                    lastStt.set(request);
                    if (failStatus != null) {
                        obs.onError(failStatus.asRuntimeException());
                        return;
                    }
                    obs.onNext(SpeechToTextResponse.newBuilder().setText("안녕").setLanguage("ko").build());
                    obs.onCompleted();
                }

                @Override
                public void textToSpeech(TextToSpeechRequest request,
                                         StreamObserver<TextToSpeechResponse> obs) {
                    lastTts.set(request);
                    if (failStatus != null) {
                        obs.onError(failStatus.asRuntimeException());
                        return;
                    }
                    obs.onNext(TextToSpeechResponse.newBuilder()
                        .setAudio(ByteString.copyFromUtf8("AUDIO")).setFormat("mp3").build());
                    obs.onCompleted();
                }
            })
            .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        adapter = new AiServiceGrpcAdapter();
        ReflectionTestUtils.setField(adapter, "stub", AiServiceGrpc.newBlockingStub(channel));
    }

    @AfterEach
    void tearDown() throws Exception {
        channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    void transcribe_maps_request_and_response() {
        TranscriptResult result = adapter.transcribe(new byte[]{1, 2, 3}, "wav", "ko");

        assertThat(result.text()).isEqualTo("안녕");
        assertThat(result.language()).isEqualTo("ko");
        assertThat(lastStt.get().getFormat()).isEqualTo("wav");
        assertThat(lastStt.get().getLanguage()).isEqualTo("ko");
        assertThat(lastStt.get().getAudio().toByteArray()).containsExactly(1, 2, 3);
    }

    @Test
    void transcribe_null_language_sends_empty() {
        adapter.transcribe(new byte[]{1}, "mp3", null);
        assertThat(lastStt.get().getLanguage()).isEmpty();
    }

    @Test
    void synthesize_maps_request_and_response() {
        SynthesizedSpeech speech = adapter.synthesize("hello", "nova", 1.5, null);

        assertThat(speech.audio()).isEqualTo("AUDIO".getBytes());
        assertThat(speech.audioFormat()).isEqualTo("mp3");
        assertThat(lastTts.get().getText()).isEqualTo("hello");
        assertThat(lastTts.get().getVoice()).isEqualTo("nova");
        assertThat(lastTts.get().getSpeed()).isEqualTo(1.5);
    }

    @Test
    void transcribe_grpc_error_becomes_ai_unavailable() {
        failStatus = Status.UNAVAILABLE.withDescription("down");
        assertThatThrownBy(() -> adapter.transcribe(new byte[]{1}, "wav", "ko"))
            .isInstanceOf(AiUnavailableException.class)
            .isNotInstanceOf(AiTimeoutException.class);  // UNAVAILABLE 은 retry 대상
    }

    @Test
    void synthesize_grpc_error_becomes_ai_unavailable() {
        failStatus = Status.INTERNAL.withDescription("boom");
        assertThatThrownBy(() -> adapter.synthesize("hi", "nova", 1.0, null))
            .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void deadline_exceeded_becomes_ai_timeout_not_retryable() {
        failStatus = Status.DEADLINE_EXCEEDED.withDescription("slow");
        // AiTimeoutException(= AiUnavailableException 하위) — retry ignoreExceptions 대상 (code H1)
        assertThatThrownBy(() -> adapter.transcribe(new byte[]{1}, "wav", "ko"))
            .isInstanceOf(AiTimeoutException.class);
    }
}
