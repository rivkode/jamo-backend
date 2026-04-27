package app.backend.jamo.identity.domain.exception;

/**
 * OAuth flowSession 이 만료되었거나(또는 already consumed) 존재하지 않을 때 발생.
 * Callback 흐름의 안전 차단 — state replay / 5분 초과 모두 본 예외로 매핑.
 */
public class OAuthFlowExpiredException extends OAuthAuthenticationException {

    public OAuthFlowExpiredException(String message) {
        super(message);
    }
}
