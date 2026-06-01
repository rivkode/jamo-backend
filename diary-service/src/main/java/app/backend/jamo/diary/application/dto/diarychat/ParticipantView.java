package app.backend.jamo.diary.application.dto.diarychat;

import java.time.Instant;
import java.util.UUID;

/**
 * 참여자 조회 결과 (API_SPEC 부록 E.2 DiaryChatParticipant). isHost 는 room.hostUserId 파생.
 * displayName 은 UserSummary gRPC 조립 (NOT_FOUND/장애 시 "(unknown)"), avatarUrl 은 현 미지원 → null.
 */
public record ParticipantView(
    UUID userId,
    String displayName,
    boolean isHost,
    Instant joinedAt
) {
}
