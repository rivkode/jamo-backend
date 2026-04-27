package app.backend.jamo.identity.domain.exception;

/**
 * Refresh JWT 의 exp 가 지났을 때 발생.
 *
 * <p>SPA 는 본 예외에 매핑된 ErrorCode 를 받아 로그인 화면으로 redirect 한다.
 * 만료는 정상 라이프사이클이므로 위조/재사용과 구분해 별도 ErrorCode 로 노출
 * (PRD auth/refresh.md §9 — REFRESH_EXPIRED).
 */
public class RefreshTokenExpiredException extends RefreshTokenException {

    public RefreshTokenExpiredException(String message) {
        super(message);
    }
}
