package app.backend.jamo.identity.infrastructure.persistence.repository;

import app.backend.jamo.identity.domain.model.profile.Profile;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.ProfileRepository;
import app.backend.jamo.identity.infrastructure.persistence.entity.ProfileJpaEntity;
import app.backend.jamo.identity.infrastructure.persistence.mapper.ProfileMapper;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

@Repository
public class ProfileRepositoryImpl implements ProfileRepository {

    private final SpringDataProfileRepository jpa;

    public ProfileRepositoryImpl(SpringDataProfileRepository jpa) {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
    }

    @Override
    public Profile save(Profile profile) {
        ProfileJpaEntity entity = jpa.findById(profile.id().value())
                .map(existing -> ProfileMapper.mergeInto(existing, profile))
                .orElseGet(() -> ProfileMapper.toJpaEntity(profile));
        ProfileJpaEntity saved = jpa.save(entity);
        return ProfileMapper.toDomain(saved);
    }

    @Override
    public Optional<Profile> findById(UserId id) {
        return jpa.findById(id.value()).map(ProfileMapper::toDomain);
    }

    @Override
    public boolean existsById(UserId id) {
        return jpa.existsById(id.value());
    }
}
