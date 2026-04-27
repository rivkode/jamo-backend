package app.backend.jamo.identity.infrastructure.external;

import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.OAuthUserInfo;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Provider 별 userinfo 응답 JSON → OAuthUserInfo VO 변환 전략.
 * 새 provider 추가 시 본 인터페이스 1개 구현 + 단위 테스트 1개 + application.yaml 설정으로 완료
 * (ADR-0006 결정 5).
 */
public interface OAuthUserInfoExtractor {

    OAuthProvider provider();

    OAuthUserInfo extract(JsonNode userinfoJson);
}
