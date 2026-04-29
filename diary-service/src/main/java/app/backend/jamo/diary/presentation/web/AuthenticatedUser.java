package app.backend.jamo.diary.presentation.web;

import java.util.Objects;
import java.util.UUID;

/**
 * 검증된 access JWT 의 claims 에서 추출한 인증 사용자 컨텍스트.
 *
 * <p>{@code userId} 는 primitive {@link UUID} — 다른 BC 의 도메인 VO ({@code identity.UserId}) 직접
 * import 차단 (ADR-0005 / ArchUnit R1). diary-service 의 도메인 / 애플리케이션 계층은 외래 ID 를
 * UUID 로 받아 처리.
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
