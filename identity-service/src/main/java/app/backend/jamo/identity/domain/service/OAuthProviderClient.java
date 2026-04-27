package app.backend.jamo.identity.domain.service;

import app.backend.jamo.identity.domain.model.oauth.OAuthUserInfo;

/**
 * OAuth provider 와의 통신 port.
 * 구현체는 infrastructure/external 에 위치 (HttpOAuthProviderClient).
 *
 * 책임: provider 의 token endpoint + userinfo endpoint 를 호출해 사용자 식별 정보 반환.
 *      네트워크 오류 / provider 4xx 응답 시 OAuthAuthenticationException 의 하위 예외를 던진다.
 */
public interface OAuthProviderClient {

    OAuthUserInfo authenticate(OAuthAuthenticationRequest request);
}
