package app.backend.jamo.diary.application.dto.audio;

/**
 * 서빙용 오디오 본문 + content-type.
 */
public record AudioContent(byte[] content, String contentType) {
}
