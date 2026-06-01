package app.backend.jamo.diary.presentation.dto.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.ParticipantView;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/v1/diary-chatrooms/{roomId}/participants 응답 (API_SPEC E2.3): {@code { items: [...] }}.
 */
public record ParticipantListResponse(List<Item> items) {

    /** API_SPEC DiaryChatParticipant: {@code { user:{userId,username,avatarUrl}, isHost, joinedAt }}. */
    public record Item(UserRef user, boolean isHost, Instant joinedAt) {
    }

    /** avatarUrl 은 현 미지원 → null (diary author 정합). */
    public record UserRef(String userId, String username, String avatarUrl) {
    }

    public static ParticipantListResponse from(List<ParticipantView> views) {
        return new ParticipantListResponse(views.stream()
            .map(v -> new Item(
                new UserRef(v.userId().toString(), v.displayName(), null),
                v.isHost(),
                v.joinedAt()))
            .toList());
    }
}
