package app.backend.jamo.identity.infrastructure.persistence.repository;

import app.backend.jamo.identity.infrastructure.persistence.entity.ProfileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataProfileRepository extends JpaRepository<ProfileJpaEntity, UUID> {
}
