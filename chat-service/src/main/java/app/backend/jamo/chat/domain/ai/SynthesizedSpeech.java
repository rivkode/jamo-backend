package app.backend.jamo.chat.domain.ai;

/** TTS 결과 — 합성 음성 바이너리 + 포맷(mp3 등). */
public record SynthesizedSpeech(byte[] audio, String audioFormat) {
}
