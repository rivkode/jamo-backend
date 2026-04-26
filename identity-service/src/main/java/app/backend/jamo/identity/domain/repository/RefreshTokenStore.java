package app.backend.jamo.identity.domain.repository;

import app.backend.jamo.identity.domain.model.auth.RefreshTokenRecord;
import app.backend.jamo.identity.domain.model.user.UserId;

import java.util.Optional;

public interface RefreshTokenStore {

    void store(RefreshTokenRecord record);

    Optional<RefreshTokenRecord> find(UserId userId, String sessionId);

    void delete(UserId userId, String sessionId);
}
