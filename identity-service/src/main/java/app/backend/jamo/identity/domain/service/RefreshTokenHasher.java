package app.backend.jamo.identity.domain.service;

/**
 * Refresh JWT 의 hash 계산 port (decisions/auth/refresh-token-hash.md).
 *
 * <p>구현체는 HMAC-SHA256 + pepper (infrastructure/security). Refresh JWT 자체는
 * 클라이언트가 보유하고, 서버는 hash 만 Redis 에 보관해 탈취 시 token reuse 검증에
 * 사용 (PR4 refresh rotation 에서 활용).
 */
public interface RefreshTokenHasher {

    String hash(String refreshTokenJwt);
}
