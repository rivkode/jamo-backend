package app.backend.jamo.chat.domain.ai;

/**
 * ai-service AiService(STT/TTS) 호출 추상화 port (ADR-0003 — chat-service 만 ai-service 호출).
 * 구현은 infrastructure 의 gRPC 어댑터 (Deadline + Resilience4j CB/Retry/fallback).
 */
public interface AiSpeechPort {

    /** 음성 → 텍스트. 실패 시 {@link AiUnavailableException}. */
    TranscriptResult transcribe(byte[] audio, String audioFormat, String language);

    /** 텍스트 → 음성. 실패 시 {@link AiUnavailableException}. */
    SynthesizedSpeech synthesize(String text, String voice, double speed, String language);
}
