package app.backend.jamo.identity.application.dto;

import app.backend.jamo.identity.domain.model.user.UserId;

import java.util.Objects;

/**
 * 본인 프로필 부분 수정 command — PATCH 의미 (null = 변경 없음).
 *
 * <p>VO 검증은 Service 안에서 수행 — Presentation 의 {@code @Valid} 는 단순 길이/형식 fast-fail.
 * VO 의 invariant 검증이 SoT.
 *
 * <p>빈 문자열 의미 (decisions/identity/profile-prd-evaluation.md §결정 #3.1):
 * <ul>
 *   <li>{@code displayName} 빈 문자열 → 400</li>
 *   <li>{@code bio} 빈 문자열 → null 정규화 (소개 제거 — Service 가 unsetBio 호출)</li>
 *   <li>{@code avatarUrl} 빈 문자열 → null 정규화 (아바타 제거 — Service 가 unsetAvatarUrl 호출)</li>
 *   <li>{@code locale} 빈 문자열 → 400</li>
 * </ul>
 */
public record UpdateMyProfileCommand(
        UserId userId,
        String displayName,
        String bio,
        String avatarUrl,
        String locale
) {

    public UpdateMyProfileCommand {
        Objects.requireNonNull(userId, "userId");
    }
}
