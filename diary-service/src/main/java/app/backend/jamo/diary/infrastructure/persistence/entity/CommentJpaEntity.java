package app.backend.jamo.diary.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * comments 테이블 매핑.
 *
 * <p>ADR-0005 정합: diaryId / authorId / parentId 는 외래 ID 컬럼만 보유하고 JPA 연관관계와
 * DB FK constraint 는 사용하지 않는다.
 */
@Entity
@Getter
@Table(name = "comments")
public class CommentJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "diary_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID diaryId;

    @Column(name = "author_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID authorId;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    @Column(name = "parent_id", columnDefinition = "BINARY(16)")
    private UUID parentId;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CommentJpaEntity() {
    }

    public CommentJpaEntity(
        UUID id,
        UUID diaryId,
        UUID authorId,
        String content,
        UUID parentId,
        int likeCount,
        Instant createdAt
    ) {
        this.id = id;
        this.diaryId = diaryId;
        this.authorId = authorId;
        this.content = content;
        this.parentId = parentId;
        this.likeCount = likeCount;
        this.createdAt = createdAt;
    }

    // setter 는 Mapper mergeInto 패턴으로만 호출. 댓글 내용 수정은 Non-Goals 이므로 likeCount 만 갱신한다.
    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }
}
