package app.backend.jamo.diary.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * diary_chat_participants 매핑 — surrogate BIGINT PK + UNIQUE(room_id, user_id) 자연키.
 *
 * <p>isHost 미저장 (room.host_user_id 파생, v2 §3). JPA 연관관계 없음 (ADR-0005).
 */
@Entity
@Getter
@Table(name = "diary_chat_participants")
public class ChatParticipantJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected ChatParticipantJpaEntity() {
    }

    public ChatParticipantJpaEntity(Long id, Long roomId, UUID userId, Instant joinedAt) {
        this.id = id;
        this.roomId = roomId;
        this.userId = userId;
        this.joinedAt = joinedAt;
    }
}
