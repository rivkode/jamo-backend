package app.backend.jamo.identity.presentation.web;

import app.backend.jamo.identity.domain.model.user.UserId;

import java.util.Objects;

/**
 * 검증된 access JWT 의 claims 에서 추출한 인증 사용자 컨텍스트.
 * Controller 가 {@link LoginUser} 파라미터로 주입받는다.
 */
public record AuthenticatedUser(UserId userId, String sessionId, String deviceId) {

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
