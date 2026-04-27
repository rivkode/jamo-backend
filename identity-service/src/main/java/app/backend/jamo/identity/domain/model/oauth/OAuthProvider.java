package app.backend.jamo.identity.domain.model.oauth;

import app.backend.jamo.identity.domain.exception.UnsupportedOAuthProviderException;

import java.util.Locale;
import java.util.Objects;

public enum OAuthProvider {
    KAKAO,
    NAVER,
    GOOGLE;

    public static OAuthProvider fromExternal(String raw) {
        Objects.requireNonNull(raw, "provider");
        String upper = raw.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "KAKAO" -> KAKAO;
            case "NAVER" -> NAVER;
            case "GOOGLE" -> GOOGLE;
            case "LOCAL" -> throw new UnsupportedOAuthProviderException("LOCAL is not an OAuth provider");
            default -> throw new UnsupportedOAuthProviderException("unknown provider");
        };
    }
}
