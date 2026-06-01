package app.backend.jamo.chat.presentation.dto;

/** chat-service API 표준 오류 코드. */
public enum ChatErrorCode {
    /** 요청 검증 실패 (HTTP 400). */
    CHAT_VALIDATION_FAILED,
    /** 업로드 크기 초과 (HTTP 413). */
    AUDIO_TOO_LARGE,
    /** 저장 음성 부재 (HTTP 404). */
    AUDIO_NOT_FOUND,
    /** ai-service 일시 장애 / Circuit open / deadline (HTTP 503). */
    AI_UNAVAILABLE,
    /** 인증 실패 (HTTP 401). */
    UNAUTHORIZED,
    /** 매핑되지 않은 서버 오류 (HTTP 500). */
    INTERNAL_ERROR
}
