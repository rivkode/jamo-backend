package app.backend.jamo.identity.presentation.dto;

/**
 * User 도메인 API 응답의 표준 오류 코드.
 *
 * <p>PR #19 PRD §9 + security review (PR5-b H2) 결정에 따라 도메인 예외의 raw message 는
 * 노출하지 않고 본 enum 의 고정 코드만 응답. SPA 가 코드별 사용자 메시지를 책임진다.
 *
 * <p>{@link AuthErrorCode} 와 분리한 이유: 도메인별 ErrorCode 그룹화 (PR4-c deferral M1).
 * 향후 `IdentityErrorCode` 통합 / 그룹화 시 본 enum 도 합병 후보.
 */
public enum UserErrorCode {

    /** 요청 body 검증 실패 (HTTP 400) — Bean Validation, 형식 오류 등 */
    VALIDATION_FAILED,

    /** 검증코드 불일치 — 잔여 시도 횟수 / 누적 시도 횟수는 응답에 포함 금지 (security H2). */
    VALIDATION_CODE_MISMATCH,

    /** 검증코드가 만료되었거나 발급되지 않음 — enumeration 회피를 위해 두 케이스 통합. */
    VALIDATION_CODE_EXPIRED,

    /** 시도 한도 초과로 코드 잠김 — 사용자는 재발급 필요. */
    VALIDATION_CODE_LOCKED,

    /** 발송 rate limit 초과 (HTTP 429) — 30초 쿨다운 또는 1일 한도 도달. */
    VALIDATION_RATE_LIMITED,

    /**
     * createUser 진입 시 이메일 검증 flag 가 없거나 만료 (HTTP 400).
     * 미발급/만료 사유는 enumeration 회피를 위해 응답에 분리하지 않음 (PR6-c).
     */
    EMAIL_NOT_VALIDATED,

    /**
     * LOCAL 가입 한정 이메일 중복 (HTTP 409).
     * OAuth 가입자와의 email 충돌은 본 코드 범위 밖 (ADR-0006 결정 4).
     * accepted risk: 검증 코드 통과 사용자에 한해 LOCAL 가입자 enumeration window 존재
     * (decisions/identity/local-credential-deployment-checklist.md 결정 3).
     */
    EMAIL_ALREADY_REGISTERED,

    /** 매핑되지 않은 모든 서버 오류 (HTTP 500) — sanitize 책임. */
    INTERNAL_ERROR
}
