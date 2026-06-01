package app.backend.jamo.diary.presentation.dto.diarychat;

/**
 * diarychat API 표준 오류 코드.
 */
public enum ChatErrorCode {

    /** 요청 검증 실패 (HTTP 400) — Bean Validation / UUID·roomId 파싱 / malformed body. */
    CHAT_VALIDATION_FAILED,

    /** 방 부재 / 비공개 일기 비작성자 / 삭제된 방 / 비참여 IDOR (HTTP 404). */
    CHAT_ROOM_NOT_FOUND,

    /** 참여자지만 권한 없음 — ai-toggle 비호스트 (HTTP 403). */
    CHAT_ROOM_FORBIDDEN,

    /** 인증 실패 (HTTP 401). */
    UNAUTHORIZED,

    /** 매핑되지 않은 서버 오류 (HTTP 500). */
    INTERNAL_ERROR
}
