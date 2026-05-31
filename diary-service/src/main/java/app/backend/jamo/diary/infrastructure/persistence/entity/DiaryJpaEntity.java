package app.backend.jamo.diary.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * diaries 테이블 매핑.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §1 / §3 / §4 / §7.
 *
 * <p><b>외래 ID (ADR-0005)</b>: {@code authorId} 는 다른 BC (identity-service) Aggregate ID — JPA 연관관계 /
 * FK constraint 미사용 (sentence-feedback 정합).
 *
 * <p><b>images / tags JSON</b>: Hibernate 6.4+ 의 {@link JdbcTypeCode}({@link SqlTypes#JSON}) 으로 MySQL JSON
 * 컬럼 매핑. {@code List<String>} 직접 저장 — Aggregate 내부 컬렉션이라 정규화 불요 (sentence-feedback 의 suggestions JSON 정합).
 *
 * <p><b>visibility</b>: enum name() 문자열 그대로 저장 (`@Enumerated` 미사용 — Mapper 에서 valueOf 변환).
 *
 * <p><b>카운터</b>: {@code likeCount} / {@code commentCount} 는 denormalized — Application Service 가
 * Aggregate {@code onLikeAdded} / {@code onLikeRemoved} 호출 후 같은 트랜잭션에서 save 시 갱신.
 */
@Entity
@Getter
@Table(name = "diaries")
public class DiaryJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "author_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID authorId;

    // 일기 본문 3줄 (PRD 0526_flutter.md §2.3 / DiaryLines VO). 각 1..200 code points.
    // VARCHAR(800) = 200 cp × 4 bytes (utf8mb4 surrogate 최악). 3컬럼 고정 (사용자 결정 — JSON 아님).
    @Column(name = "line1", nullable = false, length = 800)
    private String line1;

    @Column(name = "line2", nullable = false, length = 800)
    private String line2;

    @Column(name = "line3", nullable = false, length = 800)
    private String line3;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "images", nullable = false, columnDefinition = "JSON")
    private List<String> images;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", nullable = false, columnDefinition = "JSON")
    private List<String> tags;

    @Column(name = "visibility", nullable = false, length = 16)
    private String visibility;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DiaryJpaEntity() {
    }

    public DiaryJpaEntity(
        UUID id,
        UUID authorId,
        String line1,
        String line2,
        String line3,
        List<String> images,
        List<String> tags,
        String visibility,
        int likeCount,
        int commentCount,
        Instant createdAt
    ) {
        this.id = id;
        this.authorId = authorId;
        this.line1 = line1;
        this.line2 = line2;
        this.line3 = line3;
        this.images = images;
        this.tags = tags;
        this.visibility = visibility;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.createdAt = createdAt;
    }

    // setter — Mapper.mergeInto 호출용. Slice 3-a 부터 본문(line1/2/3) / images / tags / visibility 도 작성자
    // 수정에 의해 변경 가능 (PRD 0526_flutter.md §2.4 / PUT /diaries/{id}). id / authorId / createdAt 은 immutable.
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }
    public void setLine1(String line1) { this.line1 = line1; }
    public void setLine2(String line2) { this.line2 = line2; }
    public void setLine3(String line3) { this.line3 = line3; }
    public void setImages(List<String> images) { this.images = images; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
}
