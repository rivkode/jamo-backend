package app.backend.jamo.identity.presentation.dto;

import app.backend.jamo.identity.application.dto.MyProfileResult;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/v1/profiles/me, PATCH /api/v1/profiles/me 의 Response body — private 8 필드.
 *
 * <p>identity 5 (id / email / displayName / providers / createdAt) + 외형 3 (bio / avatarUrl / locale).
 * 본인 조회 / PATCH 모두 동일 응답 (decisions/identity/profile-prd-evaluation.md §결정 #1).
 *
 * <p><b>PRD 0526_flutter.md §1.5 정합 (Slice 2)</b>:
 * <ul>
 *   <li>{@code username} alias — {@code displayName} 동의어.</li>
 *   <li>{@code provider} 단수 alias — {@code providers.get(0)} (백엔드는 multi-OAuth 지원이라 복수,
 *       frontend 는 단일 가정). providers 가 비어있으면 null. multi-account-linking 도입 시 frontend 가
 *       {@code providers} 배열을 읽도록 전환.</li>
 *   <li>기존 {@code displayName} / {@code providers} 필드도 그대로 노출 — 양방향 호환.</li>
 * </ul>
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

    /** PRD §1.5 alias — frontend 호환 필드명. */
    @JsonProperty("username")
    public String username() {
        return displayName;
    }

    /** PRD §1.5 alias — 단일 OAuth provider 가정 (백엔드는 multi). 빈 리스트 시 null. */
    @JsonProperty("provider")
    public String provider() {
        return (providers == null || providers.isEmpty()) ? null : providers.get(0);
    }

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
