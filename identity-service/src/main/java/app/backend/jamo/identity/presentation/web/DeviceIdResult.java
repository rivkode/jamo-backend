package app.backend.jamo.identity.presentation.web;

import java.util.Objects;

/**
 * {@link DeviceIdResolver#resolve} 의 결과.
 * {@code isNewlyGenerated} 가 true 면 controller 가 응답에 device cookie 를 set 해야 함.
 */
public record DeviceIdResult(String deviceId, boolean isNewlyGenerated) {

    public DeviceIdResult {
        Objects.requireNonNull(deviceId, "deviceId");
        if (deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
    }
}
