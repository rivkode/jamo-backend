package app.backend.jamo.identity.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * POST /api/v1/auth/exchange / refresh / login 의 성공 응답.
 *
 * <p>{@code expiresInSeconds} 는 access token 의 TTL (refresh token TTL 은 클라이언트가
 * 알 필요 없음 — refresh API 가 갱신).
 *
 * <p><b>PRD 0526_flutter.md §0.2 정합 (Slice 2)</b>:
 * <ul>
 *   <li>{@code expiresIn} alias ({@code expiresInSeconds} 동의어).</li>
 *   <li>{@code tokenType} 고정 값 {@code "Bearer"} — frontend AuthenticatedHttpClient 가 헤더 조립 시 사용.</li>
 * </ul>
 * 기존 {@code expiresInSeconds} 필드도 그대로 노출.
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

    /** PRD §0.2 alias — frontend 호환 필드명. */
    @JsonProperty("expiresIn")
    public long expiresIn() {
        return expiresInSeconds;
    }

    /** PRD §0.2 — Bearer scheme 고정. */
    @JsonProperty("tokenType")
    public String tokenType() {
        return "Bearer";
    }
}
