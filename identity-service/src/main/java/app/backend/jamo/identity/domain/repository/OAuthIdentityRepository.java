package app.backend.jamo.identity.domain.repository;

import app.backend.jamo.identity.domain.model.oauth.OAuthIdentity;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.ProviderUserId;
import app.backend.jamo.identity.domain.model.user.UserId;

import java.util.List;
import java.util.Optional;

public interface OAuthIdentityRepository {

    Optional<OAuthIdentity> findByProviderAndProviderUserId(OAuthProvider provider, ProviderUserId providerUserId);

    List<OAuthIdentity> findAllByUserId(UserId userId);
}
