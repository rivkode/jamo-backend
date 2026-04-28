package app.backend.jamo.identity.presentation.dto;

/**
 * Profile 도메인 API 응답의 표준 오류 코드.
 *
 * <p>{@link UserErrorCode} / {@link AuthErrorCode} 와 분리 — 도메인별 ErrorCode 그룹화 패턴
 * (PR4-c deferral M1). 향후 `IdentityErrorCode` 통합 시 합병 후보.
 */
public enum ProfileErrorCode {

    /** 요청 body 검증 실패 (HTTP 400) — Bean Validation, VO 형식 오류. */
    VALIDATION_FAILED,

    /** displayName 변경 빈도 제한 (HTTP 400) — 7일 1회. 잔여 시간 응답 미포함 (운영 단순화). */
    DISPLAY_NAME_CHANGE_TOO_FREQUENT,

    /** 타 사용자 조회 시 대상 부재 (HTTP 404) — `/profiles/{userId}` 한정. */
    USER_NOT_FOUND,

    /** 매핑되지 않은 모든 서버 오류 (HTTP 500) — sanitize 책임. 본인 조회 시 user 부재도 본 코드 (시스템 invariant 위반). */
    INTERNAL_ERROR
}
