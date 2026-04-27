package app.backend.jamo.identity.domain.model.auth;

import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * OAuth start 와 callback 사이에 보관해야 하는 흐름 컨텍스트 (ADR-0006 결정 1, 2).
 *
 * state 가 단일 키이고 cookie 와 Redis 둘 다 같은 state 를 보관:
 *   - cookie: state 만 (HttpOnly + 5분)
 *   - Redis : 본 VO 전체 (TTL 5분, GETDEL atomic consume)
 *
 * pkceVerifier 는 provider 가 PKCE 미지원이면 null.
 *
 * <p><b>주의 — pkceVerifier 접근 시</b>: record 의 자동 accessor {@code pkceVerifier()}
 * 는 nullable 을 그대로 반환한다. 호출자는 항상 {@link #pkceVerifierOpt()} 를 사용해
 * NPE 위험을 회피할 것 (code review H3).
 */
public record OAuthFlowSession(
        AuthState state,
        OAuthProvider provider,
        PkceVerifier pkceVerifier,
        String deviceId,
        String redirectUri,
        Instant issuedAt,
        Instant expiresAt
) {
    public OAuthFlowSession {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(deviceId, "deviceId");
        if (deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
        Objects.requireNonNull(redirectUri, "redirectUri");
        if (redirectUri.isBlank()) {
            throw new IllegalArgumentException("redirectUri must not be blank");
        }
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }

    public Optional<PkceVerifier> pkceVerifierOpt() {
        return Optional.ofNullable(pkceVerifier);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
