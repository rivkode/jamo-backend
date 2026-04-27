package app.backend.jamo.identity.application.dto;

import java.util.Objects;

/**
 * OAuth callback 처리 결과. Controller (PR3-c) 가 이 정보를 SPA 로 어떻게 전달할지
 * (frontend redirect URL 의 query param vs cookie) 는 PR3-c 의 결정.
 *
 * <p>{@code isNewUser} / {@code displayNameTruncated} 는 ADR-0006 결정 3·4 enforcement —
 * SPA 가 사용자에게 신규 가입 환영 / displayName 수정 안내를 띄우도록 노출.
 */
public record OAuthCallbackResult(
        String authCode,
        boolean isNewUser,
        boolean displayNameTruncated
) {
    public OAuthCallbackResult {
        Objects.requireNonNull(authCode, "authCode");
        if (authCode.isBlank()) {
            throw new IllegalArgumentException("authCode must not be blank");
        }
    }
}
