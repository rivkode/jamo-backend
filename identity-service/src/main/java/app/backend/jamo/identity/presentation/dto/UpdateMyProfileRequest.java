package app.backend.jamo.identity.presentation.dto;

import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/profiles/me 의 Request body — 화이트리스트 4 필드 (모두 nullable).
 *
 * <p>PATCH 의미 (decisions/identity/profile-prd-evaluation.md §결정 #3.1):
 * <ul>
 *   <li>{@code null} = 변경 없음 (no-op)</li>
 *   <li>빈 문자열 — {@code displayName} 400, {@code bio} null 정규화, {@code avatarUrl} null 정규화, {@code locale} 400</li>
 * </ul>
 *
 * <p>Bean Validation 1차 방어 — Domain VO 가 SoT 검증을 함. 본 어노테이션은 fast-fail 용도.
 *
 * <p>email / providers / id / createdAt 등 화이트리스트 외 필드는 본 record 에 정의되지 않으므로
 * 클라이언트가 보내도 {@code @JsonIgnoreProperties(ignoreUnknown = true)} 기본 동작으로 무시.
 */
public record UpdateMyProfileRequest(
        @Size(min = 1, max = 32, message = "displayName must be between 1 and 32 characters")
        String displayName,

        @Size(max = 200, message = "bio must be at most 200 characters")
        String bio,

        @Size(max = 500, message = "avatarUrl must be at most 500 characters")
        String avatarUrl,

        @Size(max = 8, message = "locale must be at most 8 characters")
        String locale
) {
}
