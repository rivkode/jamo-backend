package app.backend.jamo.identity.application.service;

import app.backend.jamo.identity.application.dto.OAuthCallbackCommand;
import app.backend.jamo.identity.application.dto.OAuthCallbackResult;
import app.backend.jamo.identity.domain.exception.OAuthFlowExpiredException;
import app.backend.jamo.identity.domain.exception.OAuthStateInvalidException;
import app.backend.jamo.identity.domain.model.auth.AuthState;
import app.backend.jamo.identity.domain.model.auth.AuthorizationCode;
import app.backend.jamo.identity.domain.model.auth.AuthorizationCodeGenerator;
import app.backend.jamo.identity.domain.model.auth.OAuthFlowSession;
import app.backend.jamo.identity.domain.model.auth.PkceVerifier;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.oauth.OAuthUserInfo;
import app.backend.jamo.identity.domain.model.oauth.ProviderUserId;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.User;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.domain.repository.AuthorizationCodeStore;
import app.backend.jamo.identity.domain.repository.OAuthFlowSessionStore;
import app.backend.jamo.identity.domain.service.OAuthAuthenticationRequest;
import app.backend.jamo.identity.domain.service.OAuthProviderClient;
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
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthCallbackServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-27T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OAuthFlowSessionStore flowSessionStore;
    private OAuthProviderClient providerClient;
    private UserRegistrationService userRegistrationService;
    private AuthorizationCodeStore authCodeStore;
    private OAuthCallbackService service;

    @BeforeEach
    void setUp() {
        flowSessionStore = mock(OAuthFlowSessionStore.class);
        providerClient = mock(OAuthProviderClient.class);
        userRegistrationService = mock(UserRegistrationService.class);
        authCodeStore = mock(AuthorizationCodeStore.class);
        AuthorizationCodeGenerator gen = new AuthorizationCodeGenerator(new SecureRandom());

        OAuthProviderProperties properties = new OAuthProviderProperties(
                Duration.ofSeconds(60),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Map.of("kakao", new ProviderConfig(
                        "c", "s", "https://app/cb/kakao",
                        "https://k/auth", "https://k/token", "https://k/userinfo",
                        "scope", false)),
                new StateCookieConfig(null, false, "Lax", Duration.ofMinutes(5))
        );

        service = new OAuthCallbackService(
                flowSessionStore, providerClient, userRegistrationService,
                gen, authCodeStore, properties, CLOCK);
    }

    private OAuthFlowSession sampleFlowSession(AuthState state, OAuthProvider provider, PkceVerifier verifier) {
        return new OAuthFlowSession(
                state, provider, verifier, "device-1",
                "https://app/cb/" + provider.name().toLowerCase(),
                NOW.minusSeconds(30),
                NOW.plus(Duration.ofMinutes(4))
        );
    }

    private User sampleUser() {
        UserId id = UserId.generate();
        return User.restore(id, new DisplayName("jamo"), new Email("u@k.com"),
                NOW, NOW, Collections.emptyList());
    }

    @Test
    void handle_happy_path_consumes_session_calls_provider_registers_user_and_stores_authcode() {
        AuthState state = AuthState.random();
        OAuthFlowSession session = sampleFlowSession(state, OAuthProvider.KAKAO, null);
        when(flowSessionStore.consume(state)).thenReturn(Optional.of(session));

        OAuthUserInfo userInfo = OAuthUserInfo.of(
                new ProviderUserId("kakao-1"), "jamo", new Email("u@k.com"));
        when(providerClient.authenticate(any())).thenReturn(userInfo);

        User user = sampleUser();
        when(userRegistrationService.findOrRegister(eq(OAuthProvider.KAKAO), eq(userInfo), eq(NOW)))
                .thenReturn(new UserRegistrationResult(user, true, false));

        OAuthCallbackResult result = service.handle(new OAuthCallbackCommand(
                OAuthProvider.KAKAO, "code-x", state, state));

        assertThat(result.authCode()).isNotBlank();
        assertThat(result.isNewUser()).isTrue();
        assertThat(result.displayNameTruncated()).isFalse();

        ArgumentCaptor<AuthorizationCode> captor = ArgumentCaptor.forClass(AuthorizationCode.class);
        verify(authCodeStore).store(captor.capture());
        AuthorizationCode stored = captor.getValue();
        assertThat(stored.userId()).isEqualTo(user.id());
        assertThat(stored.deviceId()).isEqualTo("device-1");
        assertThat(stored.issuedAt()).isEqualTo(NOW);
        assertThat(stored.expiresAt()).isEqualTo(NOW.plus(Duration.ofSeconds(60)));
        assertThat(stored.value()).isEqualTo(result.authCode());
    }

    @Test
    void handle_passes_pkce_verifier_to_provider_when_session_has_one() {
        AuthState state = AuthState.random();
        PkceVerifier verifier = PkceVerifier.random(new SecureRandom());
        OAuthFlowSession session = sampleFlowSession(state, OAuthProvider.KAKAO, verifier);
        when(flowSessionStore.consume(state)).thenReturn(Optional.of(session));
        when(providerClient.authenticate(any())).thenReturn(
                OAuthUserInfo.withoutEmail(new ProviderUserId("k1"), "jamo"));
        when(userRegistrationService.findOrRegister(any(), any(), any()))
                .thenReturn(new UserRegistrationResult(sampleUser(), true, false));

        service.handle(new OAuthCallbackCommand(OAuthProvider.KAKAO, "code", state, state));

        ArgumentCaptor<OAuthAuthenticationRequest> req = ArgumentCaptor.forClass(OAuthAuthenticationRequest.class);
        verify(providerClient).authenticate(req.capture());
        assertThat(req.getValue().pkceCodeVerifierOpt()).contains(verifier.value());
    }

    @Test
    void handle_throws_state_invalid_when_cookie_state_missing() {
        AuthState state = AuthState.random();

        assertThatThrownBy(() -> service.handle(new OAuthCallbackCommand(
                OAuthProvider.KAKAO, "code", state, null)))
                .isInstanceOf(OAuthStateInvalidException.class);

        verify(flowSessionStore, never()).consume(any());
        verify(providerClient, never()).authenticate(any());
    }

    @Test
    void handle_throws_state_invalid_when_cookie_state_mismatches() {
        AuthState received = AuthState.random();
        AuthState cookie = AuthState.random();

        assertThatThrownBy(() -> service.handle(new OAuthCallbackCommand(
                OAuthProvider.KAKAO, "code", received, cookie)))
                .isInstanceOf(OAuthStateInvalidException.class);

        verify(flowSessionStore, never()).consume(any());
    }

    @Test
    void handle_throws_flow_expired_when_session_missing_in_redis() {
        AuthState state = AuthState.random();
        when(flowSessionStore.consume(state)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(new OAuthCallbackCommand(
                OAuthProvider.KAKAO, "code", state, state)))
                .isInstanceOf(OAuthFlowExpiredException.class);

        verify(providerClient, never()).authenticate(any());
    }

    @Test
    void handle_throws_flow_expired_when_session_expires_at_passed() {
        AuthState state = AuthState.random();
        OAuthFlowSession expired = new OAuthFlowSession(
                state, OAuthProvider.KAKAO, null, "device-1", "https://app/cb/kakao",
                NOW.minus(Duration.ofMinutes(10)),
                NOW.minus(Duration.ofMinutes(5))  // already past
        );
        when(flowSessionStore.consume(state)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.handle(new OAuthCallbackCommand(
                OAuthProvider.KAKAO, "code", state, state)))
                .isInstanceOf(OAuthFlowExpiredException.class);
    }

    @Test
    void handle_throws_state_invalid_when_session_provider_differs_from_path() {
        AuthState state = AuthState.random();
        // session 의 provider 는 GOOGLE 인데 path 는 KAKAO
        OAuthFlowSession session = new OAuthFlowSession(
                state, OAuthProvider.GOOGLE, null, "device-1", "https://app/cb/google",
                NOW.minusSeconds(30), NOW.plus(Duration.ofMinutes(4))
        );
        when(flowSessionStore.consume(state)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.handle(new OAuthCallbackCommand(
                OAuthProvider.KAKAO, "code", state, state)))
                .isInstanceOf(OAuthStateInvalidException.class)
                .hasMessageContaining("provider mismatch");

        verify(providerClient, never()).authenticate(any());
    }
}
