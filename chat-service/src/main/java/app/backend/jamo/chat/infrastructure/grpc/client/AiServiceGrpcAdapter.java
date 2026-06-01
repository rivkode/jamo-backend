package app.backend.jamo.chat.infrastructure.grpc.client;

import app.backend.jamo.chat.domain.ai.AiSpeechPort;
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
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * ai-service AiService gRPC 어댑터 — {@link AiSpeechPort} 구현 (ADR-0003: chat-service 만 ai-service 호출).
 *
 * <p>Deadline: STT 65s (ai-service 60s + 마진), TTS 35s. Resilience4j CircuitBreaker + Retry + fallback
 * (CLAUDE.md NEVER — gRPC 호출 CB/Retry/Fallback 의무). 실패는 {@link AiUnavailableException} 으로 통일.
 */
@Component
@Slf4j
public class AiServiceGrpcAdapter implements AiSpeechPort {

    private static final long STT_DEADLINE_MS = 65_000;
    private static final long TTS_DEADLINE_MS = 35_000;

    @GrpcClient("ai-service")
    private AiServiceGrpc.AiServiceBlockingStub stub;

    @Override
    @CircuitBreaker(name = "ai-service", fallbackMethod = "transcribeFallback")
    @Retry(name = "ai-service")
    public TranscriptResult transcribe(byte[] audio, String audioFormat, String language) {
        try {
            SpeechToTextRequest.Builder req = SpeechToTextRequest.newBuilder()
                .setAudio(ByteString.copyFrom(audio))
                .setFormat(audioFormat == null ? "" : audioFormat);
            if (language != null) {
                req.setLanguage(language);
            }
            SpeechToTextResponse resp = stub
                .withDeadlineAfter(STT_DEADLINE_MS, TimeUnit.MILLISECONDS)
                .speechToText(req.build());
            return new TranscriptResult(resp.getText(), resp.getLanguage());
        } catch (StatusRuntimeException e) {
            throw toAiException("speechToText", e);
        }
    }

    @Override
    @CircuitBreaker(name = "ai-service", fallbackMethod = "synthesizeFallback")
    @Retry(name = "ai-service")
    public SynthesizedSpeech synthesize(String text, String voice, double speed, String language) {
        try {
            TextToSpeechRequest.Builder req = TextToSpeechRequest.newBuilder()
                .setText(text)
                .setVoice(voice == null ? "" : voice)
                .setSpeed(speed);
            if (language != null) {
                req.setLanguage(language);
            }
            TextToSpeechResponse resp = stub
                .withDeadlineAfter(TTS_DEADLINE_MS, TimeUnit.MILLISECONDS)
                .textToSpeech(req.build());
            return new SynthesizedSpeech(resp.getAudio().toByteArray(), resp.getFormat());
        } catch (StatusRuntimeException e) {
            throw toAiException("textToSpeech", e);
        }
    }

    /** deadline 초과는 retry 비대상 {@link AiTimeoutException}, 그 외 gRPC 오류는 retry 대상 {@link AiUnavailableException}. */
    private static AiUnavailableException toAiException(String op, StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        String msg = "ai-service " + op + " failed: " + code;
        if (code == Status.Code.DEADLINE_EXCEEDED || code == Status.Code.CANCELLED) {
            return new AiTimeoutException(msg, e);
        }
        return new AiUnavailableException(msg, e);
    }

    // Resilience4j fallback — Circuit open / Retry 소진 시. 사용자 흐름 보호 (정형 예외).
    @SuppressWarnings("unused")
    private TranscriptResult transcribeFallback(byte[] audio, String audioFormat, String language, Throwable t) {
        log.warn("ai-service transcribe fallback: {}", t.toString());
        throw new AiUnavailableException("speech-to-text temporarily unavailable", t);
    }

    @SuppressWarnings("unused")
    private SynthesizedSpeech synthesizeFallback(String text, String voice, double speed, String language, Throwable t) {
        log.warn("ai-service synthesize fallback: {}", t.toString());
        throw new AiUnavailableException("text-to-speech temporarily unavailable", t);
    }
}
