package app.backend.jamo.identity.domain.exception;

/**
 * OAuth provider 의 token / userinfo 호출이 네트워크 오류 또는 4xx/5xx 로 실패.
 *
 * <p>본 예외의 message 는 어댑터({@code HttpOAuthProviderClient}) 에서 sanitize 되어
 * provider 응답 본문이 포함되지 않는다 (security 결정 H1, ADR-0006 후속 항목).
 * 다만 호출자({@code @RestControllerAdvice}) 는 다음을 보장해야 한다:
 *
 * <ul>
 *   <li>응답 메시지로 본 예외의 {@code getMessage()} 를 그대로 전달하지 않는다 — 고정 ErrorCode 로 매핑.</li>
 *   <li>cause 의 stack trace 를 클라이언트 응답에 포함하지 않는다.</li>
 *   <li>운영 로그에서도 provider 의 raw 응답이 추가로 attach 되지 않도록 한다.</li>
 * </ul>
 */
public class OAuthProviderCallFailedException extends OAuthAuthenticationException {

    public OAuthProviderCallFailedException(String message) {
        super(message);
    }

    public OAuthProviderCallFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
