package app.backend.jamo.diary.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * comment_likes 테이블 매핑.
 *
 * <p>단순 fact entity. 비즈니스 유니크 키는 {@code (comment_id, user_id)} 이며 DDL UNIQUE 제약이
 * 동시 INSERT race 의 최후 보루다.
 */
@Entity
@Getter
@Table(name = "comment_likes")
public class CommentLikeJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "comment_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID commentId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CommentLikeJpaEntity() {
    }

    public CommentLikeJpaEntity(UUID id, UUID commentId, UUID userId, Instant createdAt) {
        this.id = id;
        this.commentId = commentId;
        this.userId = userId;
        this.createdAt = createdAt;
    }
}
