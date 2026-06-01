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
 * diary_chat_rooms 매핑 — 일기당 1방. id 는 BIGINT AUTO_INCREMENT (롱폴 숫자 커서 정합, v2 §1).
 *
 * <p>JPA 연관관계 없음 (ADR-0005) — diary_id/host_user_id 는 외래 UUID. diary_id UNIQUE (일기당 1방).
 */
@Entity
@Getter
@Table(name = "diary_chat_rooms")
public class DiaryChatRoomJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "diary_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID diaryId;

    @Column(name = "host_user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID hostUserId;

    @Column(name = "ai_assistant_enabled", nullable = false)
    private boolean aiAssistantEnabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected DiaryChatRoomJpaEntity() {
    }

    public DiaryChatRoomJpaEntity(Long id, UUID diaryId, UUID hostUserId, boolean aiAssistantEnabled,
                                  Instant createdAt, Instant deletedAt) {
        this.id = id;
        this.diaryId = diaryId;
        this.hostUserId = hostUserId;
        this.aiAssistantEnabled = aiAssistantEnabled;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
    }
}
