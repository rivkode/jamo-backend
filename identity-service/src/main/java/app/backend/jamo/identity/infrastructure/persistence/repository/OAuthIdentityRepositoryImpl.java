package app.backend.jamo.identity.infrastructure.persistence.repository;

import app.backend.jamo.identity.domain.model.oauth.OAuthIdentity;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.ProviderUserId;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.OAuthIdentityRepository;
import app.backend.jamo.identity.infrastructure.persistence.mapper.UserMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OAuthIdentityRepositoryImpl implements OAuthIdentityRepository {

    private final SpringDataOAuthIdentityRepository delegate;

    public OAuthIdentityRepositoryImpl(SpringDataOAuthIdentityRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<OAuthIdentity> findByProviderAndProviderUserId(OAuthProvider provider, ProviderUserId providerUserId) {
        return delegate.findByProviderAndProviderUserId(provider, providerUserId.value())
                .map(UserMapper::toOAuthIdentity);
    }

    @Override
    public List<OAuthIdentity> findAllByUserId(UserId userId) {
        return delegate.findAllByUserId(userId.value()).stream()
                .map(UserMapper::toOAuthIdentity)
                .toList();
    }
}
