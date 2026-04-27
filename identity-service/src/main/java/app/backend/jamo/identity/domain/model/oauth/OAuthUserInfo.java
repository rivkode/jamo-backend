package app.backend.jamo.identity.domain.model.oauth;

import app.backend.jamo.identity.domain.model.user.Email;

import java.util.Objects;
import java.util.Optional;

/**
 * OAuth provider 가 인증 후 알려준 사용자 식별 정보.
 *
 * email 은 provider 가 동의하지 않거나 검증 실패 시 부재 — 보조 정보로만 사용.
 * displayName 변환(길이 truncate 등)은 application 계층 책임. 본 VO 는 raw 값 보유.
 */
public final class OAuthUserInfo {

    private final ProviderUserId providerUserId;
    private final String rawNickname;
    private final Email email;

    private OAuthUserInfo(ProviderUserId providerUserId, String rawNickname, Email email) {
        this.providerUserId = Objects.requireNonNull(providerUserId, "providerUserId");
        this.rawNickname = Objects.requireNonNull(rawNickname, "rawNickname");
        if (rawNickname.isBlank()) {
            throw new IllegalArgumentException("rawNickname must not be blank");
        }
        this.email = email;
    }

    public static OAuthUserInfo of(ProviderUserId providerUserId, String rawNickname, Email email) {
        return new OAuthUserInfo(providerUserId, rawNickname, email);
    }

    public static OAuthUserInfo withoutEmail(ProviderUserId providerUserId, String rawNickname) {
        return new OAuthUserInfo(providerUserId, rawNickname, null);
    }

    public ProviderUserId providerUserId() {
        return providerUserId;
    }

    public String rawNickname() {
        return rawNickname;
    }

    public Optional<Email> email() {
        return Optional.ofNullable(email);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OAuthUserInfo other)) return false;
        return providerUserId.equals(other.providerUserId)
                && rawNickname.equals(other.rawNickname)
                && Objects.equals(email, other.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerUserId, rawNickname, email);
    }
}
