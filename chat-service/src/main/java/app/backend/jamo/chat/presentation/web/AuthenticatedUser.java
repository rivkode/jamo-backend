package app.backend.jamo.chat.presentation.web;

import java.util.Objects;
import java.util.UUID;

/**
 * 검증된 access JWT claims 에서 추출한 인증 사용자 컨텍스트.
 *
 * <p>{@code userId} 는 primitive {@link UUID} — 다른 BC 의 도메인 VO 직접 import 차단 (ADR-0005).
 */
public record AuthenticatedUser(UUID userId, String sessionId, String deviceId) {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(sessionId, "sessionId");
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        Objects.requireNonNull(deviceId, "deviceId");
        if (deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
    }
}
