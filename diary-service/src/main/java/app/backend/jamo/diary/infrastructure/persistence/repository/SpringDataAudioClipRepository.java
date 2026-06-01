package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.infrastructure.persistence.entity.AudioClipJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataAudioClipRepository extends JpaRepository<AudioClipJpaEntity, UUID> {

    Optional<AudioClipJpaEntity> findByStoredName(String storedName);
}
