package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.infrastructure.persistence.entity.ProcessedEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProcessedEventRepository extends JpaRepository<ProcessedEventJpaEntity, Long> {

    boolean existsByConsumerIdAndEventId(String consumerId, String eventId);
}
