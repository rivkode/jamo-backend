package app.backend.jamo.identity.domain.model.user;

import app.backend.jamo.identity.domain.model.oauth.OAuthIdentity;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.ProviderUserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class User {

    private final UserId id;
    private DisplayName displayName;
    private Email email;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<OAuthIdentity> oauthIdentities;

    private User(UserId id, DisplayName displayName, Email email,
                 Instant createdAt, Instant updatedAt, List<OAuthIdentity> oauthIdentities) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.email = email;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.oauthIdentities = new ArrayList<>(oauthIdentities);
    }

    public static User registerWithOAuth(OAuthProvider provider, ProviderUserId providerUserId,
                                         DisplayName displayName, Email email, Instant now) {
        UserId id = UserId.generate();
        OAuthIdentity identity = OAuthIdentity.link(id, provider, providerUserId, now);
        List<OAuthIdentity> identities = new ArrayList<>();
        identities.add(identity);
        return new User(id, displayName, email, now, now, identities);
    }

    public static User restore(UserId id, DisplayName displayName, Email email,
                               Instant createdAt, Instant updatedAt, List<OAuthIdentity> oauthIdentities) {
        return new User(id, displayName, email, createdAt, updatedAt, oauthIdentities);
    }

    public OAuthIdentity linkOAuth(OAuthProvider provider, ProviderUserId providerUserId, Instant now) {
        boolean alreadyLinked = oauthIdentities.stream()
                .anyMatch(o -> o.provider() == provider);
        if (alreadyLinked) {
            throw new IllegalStateException("provider already linked: " + provider);
        }
        OAuthIdentity identity = OAuthIdentity.link(id, provider, providerUserId, now);
        oauthIdentities.add(identity);
        this.updatedAt = now;
        return identity;
    }

    public void rename(DisplayName newName, Instant now) {
        this.displayName = Objects.requireNonNull(newName, "newName");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void updateEmail(Email newEmail, Instant now) {
        this.email = Objects.requireNonNull(newEmail, "newEmail");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public UserId id() { return id; }
    public DisplayName displayName() { return displayName; }
    public Optional<Email> email() { return Optional.ofNullable(email); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public List<OAuthIdentity> oauthIdentities() { return List.copyOf(oauthIdentities); }
}
