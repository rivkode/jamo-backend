package app.backend.jamo.identity.infrastructure.persistence.entity;

import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_oauth_identity_user"))
    private UserJpaEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 128)
    private String providerUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OAuthIdentityJpaEntity() {
    }

    public OAuthIdentityJpaEntity(UUID id, OAuthProvider provider, String providerUserId, Instant createdAt) {
        this.id = id;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UserJpaEntity getUser() { return user; }
    public OAuthProvider getProvider() { return provider; }
    public String getProviderUserId() { return providerUserId; }
    public Instant getCreatedAt() { return createdAt; }

    void setUser(UserJpaEntity user) { this.user = user; }
}
