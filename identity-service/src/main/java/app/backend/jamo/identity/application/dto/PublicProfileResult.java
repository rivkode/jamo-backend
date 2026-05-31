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
 *
 * <p>{@code diaryCount} 는 타 사용자의 <b>공개(PUBLIC) 일기 수만</b> — 비공개 수 누설 차단 (IDOR, Slice 3-b /
 * PRD 0526_flutter.md §1.6). nullable — diary-service gRPC 조회 실패 시 null.
 */
public record PublicProfileResult(
        UserId id,
        DisplayName displayName,
        Bio bio,
        AvatarUrl avatarUrl,
        Long diaryCount
) {

    public PublicProfileResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        // diaryCount nullable — gRPC 조회 실패 시 null (PRD §1.6, PUBLIC 만 카운트).
    }
}
