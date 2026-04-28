package app.backend.jamo.identity.application.dto;

import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.profile.AvatarUrl;
import app.backend.jamo.identity.domain.model.profile.Bio;
import app.backend.jamo.identity.domain.model.profile.Locale;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 본인 프로필 조회/PATCH 응답 — private 8 필드 (identity 5 + 외형 3).
 *
 * <p>nullable: {@code email} (OAuth IdP 미공개), {@code bio} / {@code avatarUrl} (미설정).
 * {@code locale} 은 non-null — Profile 미생성 시 {@link Locale#DEFAULT} 으로 합성.
 *
 * <p>Presentation layer 가 본 record 를 Response DTO 로 변환 (Phase 6-b-c).
 */
public record MyProfileResult(
        UserId id,
        Email email,
        DisplayName displayName,
        List<OAuthProvider> providers,
        Instant createdAt,
        Bio bio,
        AvatarUrl avatarUrl,
        Locale locale
) {

    public MyProfileResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(providers, "providers");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(locale, "locale");
        providers = List.copyOf(providers);
    }
}
