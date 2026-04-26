package app.backend.jamo.identity.infrastructure.persistence.repository;

import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.infrastructure.persistence.entity.OAuthIdentityJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataOAuthIdentityRepository extends JpaRepository<OAuthIdentityJpaEntity, UUID> {

    Optional<OAuthIdentityJpaEntity> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    List<OAuthIdentityJpaEntity> findAllByUserId(UUID userId);
}
