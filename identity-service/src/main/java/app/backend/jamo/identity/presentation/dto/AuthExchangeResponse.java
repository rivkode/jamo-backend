package app.backend.jamo.identity.presentation.dto;

import java.util.Objects;

/**
 * POST /api/v1/auth/exchange 의 성공 응답.
 *
 * <p>{@code expiresInSeconds} 는 access token 의 TTL (refresh token TTL 은 클라이언트가
 * 알 필요 없음 — refresh API 가 갱신).
 */
public record AuthExchangeResponse(
        String userId,
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {
    public AuthExchangeResponse {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("expiresInSeconds must be positive");
        }
    }
}
