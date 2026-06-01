package app.backend.jamo.chat.application.dto;

/** TTS 결과 — 저장 파일명 ({uuid}.{ext}). presentation 이 filePath/audioUrl 조립. */
public record SpeakResult(String storedName) {
}
