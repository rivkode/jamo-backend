package app.backend.jamo.diary.infrastructure.persistence.mapper;

import app.backend.jamo.diary.domain.model.diarychat.ChatMessage;
import app.backend.jamo.diary.domain.model.diarychat.MessageAudioUrl;
import app.backend.jamo.diary.domain.model.diarychat.MessageId;
import app.backend.jamo.diary.domain.model.diarychat.MessageText;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.infrastructure.persistence.entity.ChatMessageJpaEntity;

/**
 * ChatMessage 도메인 ↔ JpaEntity 변환. 신규(id null)는 auto-increment 위임.
 */
public final class ChatMessageMapper {

    private ChatMessageMapper() {
    }

    public static ChatMessageJpaEntity toJpaEntity(ChatMessage m) {
        Long id = (m.id() == null) ? null : m.id().value();
        return new ChatMessageJpaEntity(
            id,
            m.roomId().value(),
            m.authorUserId().orElse(null),
            m.text(),
            m.audioUrl().orElse(null),
            m.source(),
            m.createdAt()
        );
    }

    public static ChatMessage toDomain(ChatMessageJpaEntity e) {
        return ChatMessage.reconstitute(
            MessageId.of(e.getId()),
            RoomId.of(e.getRoomId()),
            e.getAuthorUserId(),
            new MessageText(e.getText()),
            e.getAudioUrl() == null ? null : new MessageAudioUrl(e.getAudioUrl()),
            e.getSource(),
            e.getCreatedAt()
        );
    }
}
