package app.backend.jamo.diary.application.dto.diarychat;

import app.backend.jamo.diary.domain.model.diarychat.MessageSource;

import java.time.Instant;
import java.util.UUID;

/**
 * 메시지 조회 결과 (API_SPEC 부록 E.2 DiaryChatMessage). authorDisplayName 은 UserSummary 조립.
 * authorUserId 는 AI/SYSTEM 시 null. source 는 wire 소문자.
 */
public record MessageView(
    long messageId,
    long roomId,
    UUID authorUserId,
    String authorDisplayName,
    String text,
    String audioUrl,
    MessageSource source,
    Instant createdAt
) {
}
