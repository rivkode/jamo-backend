package app.backend.jamo.diary.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import app.backend.jamo.diary.domain.model.diarychat.MessageSource;

import java.time.Instant;
import java.util.UUID;

/**
 * diary_chat_messages 매핑. id BIGINT AUTO_INCREMENT (롱폴 숫자 커서). ADR-0005 — FK/연관관계 없음.
 */
@Entity
@Getter
@Table(name = "diary_chat_messages")
public class ChatMessageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "author_user_id", columnDefinition = "BINARY(16)")
    private UUID authorUserId;

    @Column(name = "text", nullable = false, length = 4000)
    private String text;

    @Column(name = "audio_url", length = 2048)
    private String audioUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private MessageSource source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatMessageJpaEntity() {
    }

    public ChatMessageJpaEntity(Long id, Long roomId, UUID authorUserId, String text,
                                String audioUrl, MessageSource source, Instant createdAt) {
        this.id = id;
        this.roomId = roomId;
        this.authorUserId = authorUserId;
        this.text = text;
        this.audioUrl = audioUrl;
        this.source = source;
        this.createdAt = createdAt;
    }
}
