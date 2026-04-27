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
    private final AccountType accountType;
    private HashedPassword hashedPassword;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<OAuthIdentity> oauthIdentities;

    private User(UserId id, DisplayName displayName, Email email,
                 AccountType accountType, HashedPassword hashedPassword,
                 Instant createdAt, Instant updatedAt, List<OAuthIdentity> oauthIdentities) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.email = email;
        this.accountType = Objects.requireNonNull(accountType, "accountType");
        this.hashedPassword = hashedPassword;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.oauthIdentities = new ArrayList<>(oauthIdentities);
        validateAccountTypeInvariant();
    }

    /**
     * LOCAL 가입자가 hashedPassword 를 보유하고, OAUTH 가입자는 보유하지 않는 불변식.
     * link/merge 미지원 단계의 보수적 invariant — 향후 LOCAL+OAuth 하이브리드 도입 시 본 메서드 재검토.
     */
    private void validateAccountTypeInvariant() {
        if (accountType == AccountType.LOCAL && hashedPassword == null) {
            throw new IllegalArgumentException("LOCAL account requires hashedPassword");
        }
        if (accountType == AccountType.OAUTH && hashedPassword != null) {
            throw new IllegalArgumentException("OAUTH account must not have hashedPassword");
        }
        if (accountType == AccountType.LOCAL && !oauthIdentities.isEmpty()) {
            throw new IllegalArgumentException("LOCAL account must not have oauth identities");
        }
        if (accountType == AccountType.OAUTH && oauthIdentities.isEmpty()) {
            throw new IllegalArgumentException("OAUTH account requires at least one oauth identity");
        }
    }

    public static User registerWithOAuth(OAuthProvider provider, ProviderUserId providerUserId,
                                         DisplayName displayName, Email email, Instant now) {
        UserId id = UserId.generate();
        OAuthIdentity identity = OAuthIdentity.link(id, provider, providerUserId, now);
        List<OAuthIdentity> identities = new ArrayList<>();
        identities.add(identity);
        return new User(id, displayName, email, AccountType.OAUTH, null, now, now, identities);
    }

    public static User registerLocal(DisplayName displayName, Email email,
                                     HashedPassword hashedPassword, Instant now) {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(hashedPassword, "hashedPassword");
        UserId id = UserId.generate();
        return new User(id, displayName, email, AccountType.LOCAL, hashedPassword, now, now, new ArrayList<>());
    }

    public static User restore(UserId id, DisplayName displayName, Email email,
                               AccountType accountType, HashedPassword hashedPassword,
                               Instant createdAt, Instant updatedAt, List<OAuthIdentity> oauthIdentities) {
        return new User(id, displayName, email, accountType, hashedPassword,
                createdAt, updatedAt, oauthIdentities);
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
    public AccountType accountType() { return accountType; }
    public Optional<HashedPassword> hashedPassword() { return Optional.ofNullable(hashedPassword); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public List<OAuthIdentity> oauthIdentities() { return List.copyOf(oauthIdentities); }
}
