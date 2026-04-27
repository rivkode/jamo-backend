package app.backend.jamo.identity.application.dto;

import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;

import java.util.Objects;
import java.util.Optional;

public record OAuthStartCommand(OAuthProvider provider, String deviceId) {

    public OAuthStartCommand {
        Objects.requireNonNull(provider, "provider");
    }

    public Optional<String> deviceIdOpt() {
        return (deviceId != null && !deviceId.isBlank()) ? Optional.of(deviceId) : Optional.empty();
    }
}
