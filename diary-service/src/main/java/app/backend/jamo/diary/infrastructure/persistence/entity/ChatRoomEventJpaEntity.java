package app.backend.jamo.diary.infrastructure.persistence.entity;

import app.backend.jamo.diary.domain.model.diarychat.ChatRoomEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * diary_chat_room_events 매핑 — 롱폴 events append 로그. id BIGINT AUTO_INCREMENT (poll baseline 커서).
 */
@Entity
@Getter
@Table(name = "diary_chat_room_events")
public class ChatRoomEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private ChatRoomEventType type;

    @Column(name = "actor_user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID actorUserId;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatRoomEventJpaEntity() {
    }

    public ChatRoomEventJpaEntity(Long id, Long roomId, ChatRoomEventType type,
                                  UUID actorUserId, Boolean enabled, Instant createdAt) {
        this.id = id;
        this.roomId = roomId;
        this.type = type;
        this.actorUserId = actorUserId;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }
}
