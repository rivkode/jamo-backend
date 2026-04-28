package app.backend.jamo.identity.infrastructure.persistence.entity;

import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(
        name = "oauth_identity",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_oauth_identity_provider_user",
                columnNames = {"provider", "provider_user_id"}
        )
)
public class OAuthIdentityJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 128)
    private String providerUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OAuthIdentityJpaEntity() {
    }

    public OAuthIdentityJpaEntity(UUID id, UUID userId, OAuthProvider provider,
                                  String providerUserId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.createdAt = createdAt;
    }

    // setter 없음 — id / userId / provider / providerUserId / createdAt 모두 immutable.
}
