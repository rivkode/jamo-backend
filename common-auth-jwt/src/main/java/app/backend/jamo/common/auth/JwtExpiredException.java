package app.backend.jamo.common.auth;

/**
 * JWT 의 exp claim 이 (clockSkew 포함) 지났을 때 발생.
 *
 * <p>호출자는 만료(정상 라이프사이클) 와 다른 검증 실패(서명/issuer/audience 등 — 위조 의심)
 * 를 구분해 처리한다. 예를 들어 refresh JWT 만료는 SPA 의 재로그인 redirect 트리거이고,
 * 위조는 보안 알람·블랙리스트 트리거.
 */
public class JwtExpiredException extends JwtVerificationException {

    public JwtExpiredException(String message) {
        super(message);
    }
}
