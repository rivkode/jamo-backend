package app.backend.jamo.identity.infrastructure.persistence.repository;

import app.backend.jamo.identity.domain.model.user.AccountType;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.UserRepository;
import app.backend.jamo.identity.infrastructure.persistence.entity.OAuthIdentityJpaEntity;
import app.backend.jamo.identity.infrastructure.persistence.entity.UserJpaEntity;
import app.backend.jamo.identity.infrastructure.persistence.mapper.UserMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final SpringDataUserRepository userRepo;
    private final SpringDataOAuthIdentityRepository oauthRepo;

    public UserRepositoryImpl(SpringDataUserRepository userRepo,
                              SpringDataOAuthIdentityRepository oauthRepo) {
        this.userRepo = userRepo;
        this.oauthRepo = oauthRepo;
    }

    @Override
    public User save(User user) {
        UserJpaEntity userEntity = userRepo.findById(user.id().value())
                .map(existing -> UserMapper.mergeInto(existing, user))
                .orElseGet(() -> UserMapper.toJpaEntity(user));
        UserJpaEntity savedUser = userRepo.save(userEntity);

        List<OAuthIdentityJpaEntity> existingIdentities = oauthRepo.findAllByUserId(user.id().value());
        Set<UUID> existingIds = new HashSet<>();
        for (OAuthIdentityJpaEntity e : existingIdentities) {
            existingIds.add(e.getId());
        }

        List<OAuthIdentityJpaEntity> newOnes = user.oauthIdentities().stream()
                .filter(o -> !existingIds.contains(o.id().value()))
                .map(UserMapper::toOAuthEntity)
                .toList();

        List<OAuthIdentityJpaEntity> allIdentities = new ArrayList<>(existingIdentities);
        if (!newOnes.isEmpty()) {
            allIdentities.addAll(oauthRepo.saveAll(newOnes));
        }

        return UserMapper.toDomain(savedUser, allIdentities);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return userRepo.findById(id.value())
                .map(entity -> UserMapper.toDomain(entity, oauthRepo.findAllByUserId(entity.getId())));
    }

    @Override
    public boolean existsLocalAccountByEmail(Email email) {
        return userRepo.existsByEmailAndAccountType(email.value(), AccountType.LOCAL);
    }
}
