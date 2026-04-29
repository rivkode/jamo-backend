package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.infrastructure.persistence.entity.OutboxEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SpringDataOutboxEventRepository extends JpaRepository<OutboxEventJpaEntity, Long> {

    /**
     * 미발행 row 조회 + {@code FOR UPDATE SKIP LOCKED} — 다중 OutboxPoller 인스턴스 시 같은 row 중복 발행
     * 차단 (code-reviewer H1). MySQL 8.0+ 에서 SKIP LOCKED 지원. 트랜잭션 안에서 호출 (Poller 의 단건
     * 트랜잭션) — 잠금은 트랜잭션 종료까지 유지되어 같은 row 의 markPublished UPDATE 까지 안전.
     */
    @Query(value = "select * from outbox_event where published_at is null order by id asc limit :batch for update skip locked",
        nativeQuery = true)
    List<OutboxEventJpaEntity> findUnpublishedForUpdate(@Param("batch") int batch);

    /**
     * {@code published_at} 명시 UPDATE — JPA dirty checking 의존 회피 (code-reviewer C3). 단건 트랜잭션
     * 안에서 호출하여 send 성공 시 즉시 마킹.
     */
    @Modifying
    @Query("update OutboxEventJpaEntity o set o.publishedAt = :publishedAt where o.id = :id")
    int markPublished(@Param("id") Long id, @Param("publishedAt") Instant publishedAt);
}
