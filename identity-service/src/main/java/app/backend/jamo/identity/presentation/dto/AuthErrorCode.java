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

    /** LOCAL login — 계정 없음/OAuth-only/비밀번호 불일치 통합 (HTTP 401) */
    LOGIN_INVALID,

    /** LOCAL login — 실패 시도 한도 초과 (HTTP 429) */
    LOGIN_RATE_LIMITED,

    /** Refresh — refresh JWT exp 만료 (HTTP 401, SPA 가 재로그인 redirect) */
    REFRESH_EXPIRED,

    /**
     * Refresh — refresh JWT 위조/서명 실패/tokenType 불일치/이미 폐기된 sid 재사용.
     * reuse detection 도 본 코드로 통합 — OWASP 권고에 따라 "reuse 감지" 신호를
     * 클라이언트에 노출하지 않는다 (보상 트랜잭션은 server-side log/메트릭 으로만 가시화).
     */
    REFRESH_INVALID,

    /**
     * 인증 필요 endpoint — Authorization 헤더 부재/만료/위조/blacklist 등록된 sid 등
     * 모든 인증 실패 (HTTP 401). 만료/위조 분기를 제공하지 않는 것이 보안 표준.
     */
    UNAUTHORIZED,

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
