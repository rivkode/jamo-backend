package app.backend.jamo.diary.domain.exception;

/**
 * 저장된 오디오 clip 을 찾지 못할 때 (서빙 / 메타 조회). presentation 에서 404.
 */
public class AudioClipNotFoundException extends RuntimeException {

    public AudioClipNotFoundException(String message) {
        super(message);
    }
}
