package app.backend.jamo.identity.presentation.dto;

import app.backend.jamo.identity.application.dto.PublicProfileResult;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /api/v1/profiles/{userId} 의 Response body — public-safe 4 필드.
 *
 * <p>{@code email} / {@code providers} / {@code createdAt} / {@code locale} 노출 X
 * (UserSummaryService gRPC 정합, decisions/identity/profile-prd-evaluation.md §결정 #2).
 *
 * <p><b>PRD 0526_flutter.md §1.6 정합</b>: {@code username} alias 동시 노출 (Slice 2). {@code diaryCount} —
 * 타 사용자의 <b>공개 일기 수만</b> (Slice 3-b, 비공개 누설 차단). diary-service gRPC 실패 시 null.
 * follower / isFollowing 은 후속 plan (follow 도메인 부재).
 */
public record PublicProfileResponse(
        String id,
        String displayName,
        String bio,
        String avatarUrl,
        Long diaryCount
) {

    /** PRD §1.6 alias — frontend 호환 필드명. */
    @JsonProperty("username")
    public String username() {
        return displayName;
    }

    public static PublicProfileResponse from(PublicProfileResult result) {
        return new PublicProfileResponse(
                result.id().value().toString(),
                result.displayName().value(),
                result.bio() == null ? null : result.bio().value(),
                result.avatarUrl() == null ? null : result.avatarUrl().value(),
                result.diaryCount());
    }
}
