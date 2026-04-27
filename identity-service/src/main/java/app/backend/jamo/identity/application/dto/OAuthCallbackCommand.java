package app.backend.jamo.identity.application.dto;

import app.backend.jamo.identity.domain.model.auth.AuthState;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;

import java.util.Objects;
import java.util.Optional;

public record OAuthCallbackCommand(
        OAuthProvider provider,
        String code,
        AuthState receivedState,
        AuthState cookieState
) {
    public OAuthCallbackCommand {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(code, "code");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        Objects.requireNonNull(receivedState, "receivedState");
    }

    public Optional<AuthState> cookieStateOpt() {
        return Optional.ofNullable(cookieState);
    }
}
