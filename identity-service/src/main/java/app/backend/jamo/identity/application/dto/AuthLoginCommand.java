package app.backend.jamo.identity.application.dto;

import java.util.Objects;

public record AuthLoginCommand(String email, String password, String deviceId, String clientIp) {
    public AuthLoginCommand {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(clientIp, "clientIp must not be null");
        if (email.isBlank() || password.isEmpty() || deviceId.isBlank() || clientIp.isBlank()) {
            throw new IllegalArgumentException("email, password, deviceId and clientIp must not be blank");
        }
    }

    @Override
    public String toString() {
        return "AuthLoginCommand[email=" + email + ", password=***, deviceId="
                + deviceId + ", clientIp=" + clientIp + "]";
    }
}
