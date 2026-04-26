package app.backend.jamo.identity.infrastructure.persistence.repository;

import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.UserRepository;
import app.backend.jamo.identity.infrastructure.persistence.entity.UserJpaEntity;
import app.backend.jamo.identity.infrastructure.persistence.mapper.UserMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final SpringDataUserRepository delegate;

    public UserRepositoryImpl(SpringDataUserRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = delegate.findById(user.id().value())
                .map(existing -> UserMapper.mergeInto(existing, user))
                .orElseGet(() -> UserMapper.toJpaEntity(user));
        UserJpaEntity saved = delegate.save(entity);
        return UserMapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return delegate.findById(id.value()).map(UserMapper::toDomain);
    }
}
