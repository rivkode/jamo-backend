package app.backend.jamo.diary.domain.exception;

/**
 * 업로드 오디오가 도메인 불변식을 위반할 때 (빈 본문 / 허용 안 된 content-type / 크기 초과).
 * presentation 에서 400 (또는 413 — 크기 초과) 으로 매핑.
 */
public class InvalidAudioException extends RuntimeException {

    public InvalidAudioException(String message) {
        super(message);
    }
}
