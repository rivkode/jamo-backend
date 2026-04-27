package app.backend.jamo.identity.application.dto;

import app.backend.jamo.identity.domain.model.auth.AuthState;

import java.util.Objects;

public record OAuthStartResult(String authorizeUrl, AuthState state, String deviceId) {

    public OAuthStartResult {
        Objects.requireNonNull(authorizeUrl, "authorizeUrl");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(deviceId, "deviceId");
    }
}
