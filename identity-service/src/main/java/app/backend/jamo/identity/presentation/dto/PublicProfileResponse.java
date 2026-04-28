package app.backend.jamo.identity.presentation.dto;

import app.backend.jamo.identity.application.dto.PublicProfileResult;

/**
 * GET /api/v1/profiles/{userId} 의 Response body — public-safe 4 필드.
 *
 * <p>{@code email} / {@code providers} / {@code createdAt} / {@code locale} 노출 X
 * (UserSummaryService gRPC 정합, decisions/identity/profile-prd-evaluation.md §결정 #2).
 */
public record PublicProfileResponse(
        String id,
        String displayName,
        String bio,
        String avatarUrl
) {

    public static PublicProfileResponse from(PublicProfileResult result) {
        return new PublicProfileResponse(
                result.id().value().toString(),
                result.displayName().value(),
                result.bio() == null ? null : result.bio().value(),
                result.avatarUrl() == null ? null : result.avatarUrl().value());
    }
}
