package app.backend.jamo.diary.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * diary_likes 테이블 매핑.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §8 (별도 Aggregate, 단순 fact) / §11 (회원 탈퇴 cascade).
 *
 * <p><b>UNIQUE 제약</b>: DDL `(diary_id, user_id)` UNIQUE — drift 방지 최후 보루
 * (ToggleDiaryLikeService javadoc 의 동시성 race window 정합). UNIQUE 위반 시 DataIntegrityViolationException
 * (Spring) 으로 전파.
 *
 * <p><b>외래 ID (ADR-0005)</b>: {@code diaryId} / {@code userId} JPA 연관관계 / FK 미사용.
 *
 * <p>본 entity 는 단순 fact — setter 없음 (Aggregate 가 단순 fact, immutable).
 */
@Entity
@Getter
@Table(name = "diary_likes")
public class DiaryLikeJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "diary_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID diaryId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DiaryLikeJpaEntity() {
    }

    public DiaryLikeJpaEntity(UUID id, UUID diaryId, UUID userId, Instant createdAt) {
        this.id = id;
        this.diaryId = diaryId;
        this.userId = userId;
        this.createdAt = createdAt;
    }
}
