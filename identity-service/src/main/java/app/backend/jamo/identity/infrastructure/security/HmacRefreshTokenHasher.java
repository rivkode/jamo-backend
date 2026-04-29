package app.backend.jamo.identity.infrastructure.security;

import app.backend.jamo.identity.domain.service.RefreshTokenHasher;
import app.backend.jamo.identity.infrastructure.config.RefreshTokenHashProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

/**
 * HMAC-SHA256 + pepper 기반 refresh token hasher.
 *
 * <p>알고리즘 결정 근거: docs/decisions/auth/refresh-token-hash.md
 * <p>Mac 인스턴스는 thread-safe 하지 않으므로 매 호출마다 새 인스턴스 생성.
 * 호출 빈도가 낮음 (refresh 발급 시점만) — 성능 비용 미미.
 */
public class HmacRefreshTokenHasher implements RefreshTokenHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public HmacRefreshTokenHasher(RefreshTokenHashProperties properties) {
        this.key = new SecretKeySpec(
                properties.pepper().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
    }

    @Override
    public String hash(String refreshTokenJwt) {
        Objects.requireNonNull(refreshTokenJwt, "refreshTokenJwt");
        if (refreshTokenJwt.isBlank()) {
            throw new IllegalArgumentException("refreshTokenJwt must not be blank");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            byte[] hash = mac.doFinal(refreshTokenJwt.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 init failed", e);
        }
    }
}
