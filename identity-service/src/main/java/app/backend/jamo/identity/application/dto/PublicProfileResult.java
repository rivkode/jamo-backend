package app.backend.jamo.identity.application.dto;

import app.backend.jamo.identity.domain.model.profile.AvatarUrl;
import app.backend.jamo.identity.domain.model.profile.Bio;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.UserId;

import java.util.Objects;

/**
 * 타 사용자 프로필 조회 응답 — public-safe 4 필드 (id / displayName / bio / avatarUrl).
 *
 * <p>{@code email} / {@code providers} / {@code createdAt} / {@code locale} 은 cross-user 노출 X
 * (decisions/identity/profile-prd-evaluation.md §결정 #2 — UserSummaryService public-safe 정합).
 */
public record PublicProfileResult(
        UserId id,
        DisplayName displayName,
        Bio bio,
        AvatarUrl avatarUrl
) {

    public PublicProfileResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
    }
}
