package app.backend.jamo.identity.domain.repository;

import app.backend.jamo.identity.domain.model.profile.Profile;
import app.backend.jamo.identity.domain.model.user.UserId;

import java.util.Optional;

/**
 * Profile aggregate persistence port.
 *
 * <p>shared identifier 패턴 — Profile.id == User.id 이므로 단일 식별자 ({@link UserId}) 로 조회.
 * 1:1 매핑은 도메인 invariant 로 보장되므로 별도 {@code findByUserId} 미제공.
 */
public interface ProfileRepository {

    Profile save(Profile profile);

    Optional<Profile> findById(UserId id);

    boolean existsById(UserId id);
}
