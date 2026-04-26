package app.backend.jamo.identity.domain.repository;

import app.backend.jamo.identity.domain.model.auth.AuthorizationCode;

import java.util.Optional;

public interface AuthorizationCodeStore {

    void store(AuthorizationCode code);

    Optional<AuthorizationCode> consume(String codeValue);
}
