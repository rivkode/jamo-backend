package app.backend.jamo.identity.presentation.dto;

import app.backend.jamo.identity.application.dto.MyProfileResult;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/v1/profiles/me, PATCH /api/v1/profiles/me 의 Response body — private 8 필드.
 *
 * <p>identity 5 (id / email / displayName / providers / createdAt) + 외형 3 (bio / avatarUrl / locale).
 * 본인 조회 / PATCH 모두 동일 응답 (decisions/identity/profile-prd-evaluation.md §결정 #1).
 */
public record MyProfileResponse(
        String id,
        String email,
        String displayName,
        List<String> providers,
        Instant createdAt,
        String bio,
        String avatarUrl,
        String locale
) {

    public static MyProfileResponse from(MyProfileResult result) {
        return new MyProfileResponse(
                result.id().value().toString(),
                result.email() == null ? null : result.email().value(),
                result.displayName().value(),
                result.providers().stream().map(OAuthProvider::name).toList(),
                result.createdAt(),
                result.bio() == null ? null : result.bio().value(),
                result.avatarUrl() == null ? null : result.avatarUrl().value(),
                result.locale().code());
    }
}
