package app.backend.jamo.diary.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * audio_clips 테이블 매핑 — 업로드된 음성 한 건의 메타데이터 (바이너리는 파일시스템).
 *
 * <p>JPA 연관관계 없음 (ADR-0005) — owner_user_id 는 외래 UUID 컬럼. {@code stored_name} 은 UNIQUE.
 */
@Entity
@Getter
@Table(name = "audio_clips")
public class AudioClipJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "owner_user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID ownerUserId;

    @Column(name = "stored_name", nullable = false, length = 255, unique = true)
    private String storedName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AudioClipJpaEntity() {
    }

    public AudioClipJpaEntity(UUID id, UUID ownerUserId, String storedName,
                              String contentType, long sizeBytes, Instant createdAt) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.storedName = storedName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
    }
}
