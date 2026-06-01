package app.backend.jamo.diary.presentation.dto;

/**
 * audio sub-domain API 표준 오류 코드 (diary-service 의 sub-domain 별 ErrorCode enum 패턴 정합).
 */
public enum AudioErrorCode {

    /** content-type 미허용 / 빈 본문 / 형식 오류 (HTTP 400). */
    AUDIO_VALIDATION_FAILED,

    /** 업로드 크기 초과 (HTTP 413). */
    AUDIO_TOO_LARGE,

    /** 저장된 음성 부재 (HTTP 404). */
    AUDIO_NOT_FOUND,

    /** 인증 실패 (HTTP 401). */
    UNAUTHORIZED,

    /** 매핑되지 않은 서버 오류 (HTTP 500). */
    INTERNAL_ERROR
}
