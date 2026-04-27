package app.backend.jamo.identity.domain.model.auth;

import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthFlowSessionTest {

    private static final Instant NOW = Instant.parse("2026-04-27T10:00:00Z");
    private static final AuthState STATE = AuthState.random();
    private static final PkceVerifier VERIFIER = PkceVerifier.random(new SecureRandom());

    @Test
    void creates_with_pkce_verifier() {
        OAuthFlowSession s = new OAuthFlowSession(
                STATE, OAuthProvider.GOOGLE, VERIFIER,
                "device-1", "https://app/cb", NOW, NOW.plus(Duration.ofMinutes(5)));

        assertThat(s.pkceVerifierOpt()).contains(VERIFIER);
    }

    @Test
    void is_expired_returns_true_at_expires_at_boundary_inclusive() {
        // 정확히 expiresAt 인 순간도 expired (production: !now.isBefore(expiresAt))
        OAuthFlowSession s = new OAuthFlowSession(
                STATE, OAuthProvider.KAKAO, null, "d", "https://x",
                NOW, NOW.plus(Duration.ofMinutes(5)));

        assertThat(s.isExpired(NOW)).isFalse();
        assertThat(s.isExpired(NOW.plus(Duration.ofMinutes(5)).minusNanos(1))).isFalse();
        assertThat(s.isExpired(NOW.plus(Duration.ofMinutes(5)))).isTrue();
        assertThat(s.isExpired(NOW.plus(Duration.ofMinutes(6)))).isTrue();
    }

    @Test
    void creates_without_pkce_verifier() {
        OAuthFlowSession s = new OAuthFlowSession(
                STATE, OAuthProvider.NAVER, null,
                "device-1", "https://app/cb", NOW, NOW.plus(Duration.ofMinutes(5)));

        assertThat(s.pkceVerifierOpt()).isEqualTo(Optional.empty());
    }

    @Test
    void rejects_blank_device_id() {
        assertThatThrownBy(() -> new OAuthFlowSession(
                STATE, OAuthProvider.KAKAO, null, "  ", "https://app/cb",
                NOW, NOW.plus(Duration.ofMinutes(5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deviceId");
    }

    @Test
    void rejects_blank_redirect_uri() {
        assertThatThrownBy(() -> new OAuthFlowSession(
                STATE, OAuthProvider.KAKAO, null, "device-1", "",
                NOW, NOW.plus(Duration.ofMinutes(5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("redirectUri");
    }

    @Test
    void rejects_expires_at_not_after_issued_at() {
        assertThatThrownBy(() -> new OAuthFlowSession(
                STATE, OAuthProvider.KAKAO, null, "device-1", "https://app/cb",
                NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt");
    }

    @Test
    void rejects_null_state_or_provider() {
        assertThatThrownBy(() -> new OAuthFlowSession(
                null, OAuthProvider.KAKAO, null, "device-1", "https://app/cb",
                NOW, NOW.plus(Duration.ofMinutes(5))))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("state");

        assertThatThrownBy(() -> new OAuthFlowSession(
                STATE, null, null, "device-1", "https://app/cb",
                NOW, NOW.plus(Duration.ofMinutes(5))))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("provider");
    }
}
