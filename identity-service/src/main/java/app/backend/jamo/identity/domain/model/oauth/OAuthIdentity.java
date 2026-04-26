package app.backend.jamo.identity.domain.model.oauth;

import app.backend.jamo.identity.domain.model.user.UserId;

import java.time.Instant;
import java.util.Objects;

public final class OAuthIdentity {

    private final OAuthIdentityId id;
    private final UserId userId;
    private final OAuthProvider provider;
    private final ProviderUserId providerUserId;
    private final Instant createdAt;

    private OAuthIdentity(OAuthIdentityId id, UserId userId, OAuthProvider provider,
                          ProviderUserId providerUserId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.providerUserId = Objects.requireNonNull(providerUserId, "providerUserId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static OAuthIdentity link(UserId userId, OAuthProvider provider,
                                     ProviderUserId providerUserId, Instant now) {
        return new OAuthIdentity(OAuthIdentityId.generate(), userId, provider, providerUserId, now);
    }

    public static OAuthIdentity restore(OAuthIdentityId id, UserId userId, OAuthProvider provider,
                                        ProviderUserId providerUserId, Instant createdAt) {
        return new OAuthIdentity(id, userId, provider, providerUserId, createdAt);
    }

    public OAuthIdentityId id() { return id; }
    public UserId userId() { return userId; }
    public OAuthProvider provider() { return provider; }
    public ProviderUserId providerUserId() { return providerUserId; }
    public Instant createdAt() { return createdAt; }
}
