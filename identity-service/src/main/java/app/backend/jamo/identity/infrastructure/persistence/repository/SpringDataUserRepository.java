package app.backend.jamo.identity.infrastructure.persistence.repository;

import app.backend.jamo.identity.domain.model.user.AccountType;
import app.backend.jamo.identity.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

    boolean existsByEmailAndAccountType(String email, AccountType accountType);
}
