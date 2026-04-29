package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.infrastructure.persistence.entity.ProcessedEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SpringDataProcessedEventRepository extends JpaRepository<ProcessedEventJpaEntity, Long> {

    boolean existsByConsumerIdAndEventId(String consumerId, String eventId);

    /**
     * ProcessedEvent retention cleanup (D-a-5-impl-batch — code-reviewer L3 "장기 운영 disk pressure").
     * {@code processed_at < cutoff} 인 row hard-delete. retention 기간 내 같은 (consumer_id, event_id)
     * 가 다시 도달하면 멱등 보호되지만, retention 이후는 재처리 가능 — Kafka offset 이 이미 진행되어
     * 재 delivery 가능성 매우 낮음 (retention > Kafka 의 max retention).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ProcessedEventJpaEntity p where p.processedAt < :cutoff")
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff);
}
