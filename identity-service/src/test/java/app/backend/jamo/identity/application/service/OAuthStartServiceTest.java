package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.OAuthStartCommand;
import app.backend.jamo.identity.application.dto.OAuthStartResult;
import app.backend.jamo.identity.domain.exception.UnsupportedOAuthProviderException;
import app.backend.jamo.identity.domain.model.auth.OAuthFlowSession;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.repository.OAuthFlowSessionStore;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties.ProviderConfig;
import app.backend.jamo.identity.infrastructure.config.OAuthProviderProperties.StateCookieConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OAuthStartServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-27T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OAuthFlowSessionStore flowSessionStore;
    private OAuthStartService service;
    private OAuthProviderProperties properties;

    @BeforeEach
    void setUp() {
        Map<String, ProviderConfig> providers = new LinkedHashMap<>();
        providers.put("kakao", new ProviderConfig(
                "kakao-client", "kakao-secret",
                "https://app/cb/kakao",
                "https://kauth/auth", "https://kauth/token", "https://kapi/userinfo",
                "profile_nickname", false));
        providers.put("google", new ProviderConfig(
                "google-client", "google-secret",
                "https://app/cb/google",
                "https://google/auth", "https://google/token", "https://google/userinfo",
                "openid profile email", true));
        properties = new OAuthProviderProperties(
                Duration.ofSeconds(60),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                providers,
                new StateCookieConfig(null, false, "Lax", Duration.ofMinutes(5))
        );

        flowSessionStore = mock(OAuthFlowSessionStore.class);
        service = new OAuthStartService(properties, flowSessionStore, new SecureRandom(), CLOCK);
    }

    @Test
    void start_uses_supplied_device_id() {
        OAuthStartResult result = service.start(new OAuthStartCommand(OAuthProvider.KAKAO, "device-fixed"));

        assertThat(result.deviceId()).isEqualTo("device-fixed");
    }

    @Test
    void start_generates_web_device_id_when_not_supplied() {
        OAuthStartResult result = service.start(new OAuthStartCommand(OAuthProvider.KAKAO, null));

        assertThat(result.deviceId()).startsWith("web-");
    }

    @Test
    void start_generates_web_device_id_when_supplied_blank() {
        OAuthStartResult result = service.start(new OAuthStartCommand(OAuthProvider.KAKAO, "  "));

        assertThat(result.deviceId()).startsWith("web-");
    }

    @Test
    void start_stores_flow_session_with_state_provider_redirect_and_ttl() {
        service.start(new OAuthStartCommand(OAuthProvider.KAKAO, "device-1"));

        ArgumentCaptor<OAuthFlowSession> captor = ArgumentCaptor.forClass(OAuthFlowSession.class);
        verify(flowSessionStore).store(captor.capture());

        OAuthFlowSession stored = captor.getValue();
        assertThat(stored.provider()).isEqualTo(OAuthProvider.KAKAO);
        assertThat(stored.deviceId()).isEqualTo("device-1");
        assertThat(stored.redirectUri()).isEqualTo("https://app/cb/kakao");
        assertThat(stored.issuedAt()).isEqualTo(NOW);
        assertThat(stored.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void start_generates_pkce_verifier_when_provider_pkce_enabled() {
        service.start(new OAuthStartCommand(OAuthProvider.GOOGLE, "device-1"));

        ArgumentCaptor<OAuthFlowSession> captor = ArgumentCaptor.forClass(OAuthFlowSession.class);
        verify(flowSessionStore).store(captor.capture());

        assertThat(captor.getValue().pkceVerifierOpt()).isPresent();
    }

    @Test
    void start_omits_pkce_verifier_when_provider_pkce_disabled() {
        service.start(new OAuthStartCommand(OAuthProvider.KAKAO, "device-1"));

        ArgumentCaptor<OAuthFlowSession> captor = ArgumentCaptor.forClass(OAuthFlowSession.class);
        verify(flowSessionStore).store(captor.capture());

        assertThat(captor.getValue().pkceVerifierOpt()).isEmpty();
    }

    @Test
    void start_returns_authorize_url_with_required_params() {
        OAuthStartResult result = service.start(new OAuthStartCommand(OAuthProvider.KAKAO, "device-1"));

        assertThat(result.authorizeUrl()).startsWith("https://kauth/auth?");
        assertThat(result.authorizeUrl()).contains("response_type=code");
        assertThat(result.authorizeUrl()).contains("client_id=kakao-client");
        assertThat(result.authorizeUrl()).contains("state=" + result.state().value());
        assertThat(result.authorizeUrl()).doesNotContain("code_challenge");  // PKCE off
    }

    @Test
    void start_includes_pkce_challenge_when_provider_pkce_enabled() {
        OAuthStartResult result = service.start(new OAuthStartCommand(OAuthProvider.GOOGLE, "device-1"));

        assertThat(result.authorizeUrl()).contains("code_challenge=");
        assertThat(result.authorizeUrl()).contains("code_challenge_method=S256");
    }

    @Test
    void start_throws_when_provider_not_configured() {
        // properties 에 NAVER 미등록
        assertThatThrownBy(() -> service.start(new OAuthStartCommand(OAuthProvider.NAVER, "device-1")))
                .isInstanceOf(UnsupportedOAuthProviderException.class)
                .hasMessageContaining("not configured");

        verify(flowSessionStore, never()).store(any(OAuthFlowSession.class));
    }
}
