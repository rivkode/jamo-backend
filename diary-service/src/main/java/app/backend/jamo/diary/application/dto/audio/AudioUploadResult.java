package app.backend.jamo.diary.application.dto.audio;

/**
 * 업로드 결과 — presentation 이 {@code storedName} 으로 audioUrl/filePath 를 조립.
 *
 * @param storedName  저장 파일명 ({uuid}.{ext}) — 서빙 경로 {@code /audio/{storedName}} 의 키
 * @param contentType 저장된 MIME type
 * @param sizeBytes   바이트 수
 */
public record AudioUploadResult(String storedName, String contentType, long sizeBytes) {
}
