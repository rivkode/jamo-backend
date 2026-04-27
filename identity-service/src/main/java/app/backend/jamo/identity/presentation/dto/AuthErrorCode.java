package app.backend.jamo.identity.presentation.dto;

/**
 * Auth API 응답의 표준 오류 코드 (ADR-0006 후속 항목 / PR3-a security H1 의 호출자 의무).
 *
 * <p>두 용도로 사용:
 * <ul>
 *   <li>Exchange JSON 응답 (400/401/500) — {@link AuthErrorResponse} 의 code 필드</li>
 *   <li>Callback 302 redirect 의 {@code ?code=<ENUM>} query param</li>
 * </ul>
 *
 * <p>도메인 예외의 raw message 는 절대 클라이언트에 노출하지 않는다 — 본 enum 의
 * 고정 코드만 노출. SPA 가 코드별 사용자 메시지를 책임진다.
 */
public enum AuthErrorCode {

    /** Exchange — 요청 body 검증 실패 (HTTP 400) */
    VALIDATION_FAILED,

    /** Exchange — authorization code 가 없거나 만료/이미 사용됨 (HTTP 401) */
    AUTH_CODE_INVALID,

    /** Callback — provider 가 authorize 단계에서 error 또는 code 부재 */
    OAUTH_AUTHORIZATION_FAILED,

    /** Callback — state cookie 부재/불일치 또는 provider mismatch */
    OAUTH_STATE_INVALID,

    /** Callback — flowSession Redis 부재 또는 5분 TTL 만료 */
    OAUTH_FLOW_EXPIRED,

    /** Callback — provider token / userinfo 호출 실패 (4xx, 5xx, IO error 통합) */
    OAUTH_PROVIDER_UNAVAILABLE,

    /** 매핑되지 않은 모든 서버 오류 (HTTP 500) — sanitize 책임 */
    INTERNAL_ERROR
}
