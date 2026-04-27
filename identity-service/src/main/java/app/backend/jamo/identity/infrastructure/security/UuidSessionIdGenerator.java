package app.backend.jamo.identity.infrastructure.security;

import app.backend.jamo.identity.domain.service.SessionIdGenerator;

import java.util.UUID;

/**
 * {@link SessionIdGenerator} 의 UUID v4 구현.
 *
 * <p>UUID v4 의 122-bit 무작위성은 충돌 확률이 무시 가능 — sid 단위 고유성 보장에 충분.
 * 운영 secret 이 아니므로 SecureRandom 의 강한 보장은 불필요 (UUID.randomUUID() 가 내부적으로
 * SecureRandom 사용).
 */
public class UuidSessionIdGenerator implements SessionIdGenerator {

    @Override
    public String newSessionId() {
        return UUID.randomUUID().toString();
    }
}
