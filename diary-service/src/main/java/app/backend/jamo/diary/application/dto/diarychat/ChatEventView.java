package app.backend.jamo.diary.application.dto.diarychat;

import app.backend.jamo.diary.domain.model.diarychat.ChatRoomEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * 롱폴 이벤트 조회 결과 (API_SPEC E.2 DiaryChatEvent). enabled 는 AI_TOGGLE_CHANGED 만 non-null.
 */
public record ChatEventView(ChatRoomEventType type, Instant at, UUID userId, Boolean enabled) {
}
