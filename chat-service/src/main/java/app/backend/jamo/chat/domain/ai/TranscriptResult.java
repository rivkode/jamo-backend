package app.backend.jamo.chat.domain.ai;

/** STT 결과 — 전사 텍스트 + 감지/입력 언어. */
public record TranscriptResult(String text, String language) {
}
