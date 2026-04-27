package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.identity.application.dto.OAuthCallbackCommand;
import app.backend.jamo.identity.application.dto.OAuthCallbackResult;
import app.backend.jamo.identity.application.dto.OAuthStartCommand;
import app.backend.jamo.identity.application.dto.OAuthStartResult;
import app.backend.jamo.identity.application.service.OAuthCallbackService;
import app.backend.jamo.identity.application.service.OAuthStartService;
import app.backend.jamo.identity.domain.exception.OAuthFlowExpiredException;
import app.backend.jamo.identity.domain.exception.OAuthProviderCallFailedException;
import app.backend.jamo.identity.domain.exception.OAuthStateInvalidException;
import app.backend.jamo.identity.domain.model.auth.AuthState;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.infrastructure.config.FrontendProperties;
import app.backend.jamo.identity.presentation.exception.AuthExceptionHandler;
import app.backend.jamo.identity.presentation.web.DeviceIdResolver;
import app.backend.jamo.identity.presentation.web.DeviceIdResult;
import app.backend.jamo.identity.presentation.web.StateCookieManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OAuthBrowserController.class)
@Import({AuthExceptionHandler.class, OAuthBrowserControllerTest.TestConfig.class})
class OAuthBrowserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OAuthStartService oAuthStartService;

    @MockitoBean
    private OAuthCallbackService oAuthCallbackService;

    @MockitoBean
    private StateCookieManager stateCookieManager;

    @MockitoBean
    private DeviceIdResolver deviceIdResolver;

    @MockitoBean
    private JwtVerifier jwtVerifier;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public FrontendProperties frontendProperties() {
            return new FrontendProperties("https://app.test");
        }
    }

    @BeforeEach
    void setUp() {
        when(deviceIdResolver.resolve(any()))
                .thenReturn(new DeviceIdResult("device-fixed-id-123", false));
    }

    @Test
    void start_redirects_302_to_provider_authorize_url() throws Exception {
        AuthState state = AuthState.random();
        when(oAuthStartService.start(any(OAuthStartCommand.class)))
                .thenReturn(new OAuthStartResult("https://provider.test/authorize?x=y", state, "device-fixed-id-123"));

        mockMvc.perform(get("/api/v1/auth/oauth/kakao/start"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://provider.test/authorize?x=y"));

        verify(stateCookieManager).set(any(), eq(OAuthProvider.KAKAO), eq(state));
        verify(deviceIdResolver, never()).setDeviceCookie(any(), any());
    }

    @Test
    void start_sets_device_cookie_when_newly_generated() throws Exception {
        when(deviceIdResolver.resolve(any()))
                .thenReturn(new DeviceIdResult("web-newly-uuid", true));
        when(oAuthStartService.start(any()))
                .thenReturn(new OAuthStartResult("https://provider.test/auth", AuthState.random(), "web-newly-uuid"));

        mockMvc.perform(get("/api/v1/auth/oauth/kakao/start"))
                .andExpect(status().isFound());

        verify(deviceIdResolver).setDeviceCookie(any(), eq("web-newly-uuid"));
    }

    @Test
    void start_returns_401_when_provider_unsupported() throws Exception {
        // OAuthProvider.fromExternal("invalid") → UnsupportedOAuthProviderException → 401
        mockMvc.perform(get("/api/v1/auth/oauth/invalid/start"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void callback_success_redirects_to_frontend_with_code() throws Exception {
        AuthState state = AuthState.random();
        when(stateCookieManager.read(any(), eq(OAuthProvider.KAKAO)))
                .thenReturn(Optional.of(state));
        when(oAuthCallbackService.handle(any(OAuthCallbackCommand.class)))
                .thenReturn(new OAuthCallbackResult("authcode-xyz", true, false));

        mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                        .param("code", "provider-code")
                        .param("state", state.value()))
                .andExpect(status().isFound())
                .andExpect(header().stringValues("Location",
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("https://app.test/auth/callback"))))
                .andExpect(header().stringValues("Location",
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("code=authcode-xyz"))))
                .andExpect(header().stringValues("Location",
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("isNew=true"))));

        verify(stateCookieManager).clear(any(), eq(OAuthProvider.KAKAO));
    }

    @Test
    void callback_redirects_to_error_when_provider_returns_error_param() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                        .param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().stringValues("Location",
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("/auth/error?code=OAUTH_AUTHORIZATION_FAILED"))));

        verify(stateCookieManager).clear(any(), eq(OAuthProvider.KAKAO));
    }

    @Test
    void callback_redirects_to_error_when_code_or_state_missing() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback"))
                .andExpect(status().isFound())
                .andExpect(header().stringValues("Location",
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("OAUTH_AUTHORIZATION_FAILED"))));
    }

    @Test
    void callback_redirects_with_state_invalid_when_service_throws_state_invalid() throws Exception {
        AuthState state = AuthState.random();
        when(stateCookieManager.read(any(), any())).thenReturn(Optional.of(state));
        doAnswer(inv -> { throw new OAuthStateInvalidException("mismatch"); })
                .when(oAuthCallbackService).handle(any());

        mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                        .param("code", "x")
                        .param("state", state.value()))
                .andExpect(status().isFound())
                .andExpect(header().stringValues("Location",
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("OAUTH_STATE_INVALID"))));

        verify(stateCookieManager).clear(any(), eq(OAuthProvider.KAKAO));
    }

    @Test
    void callback_redirects_with_flow_expired_when_service_throws_flow_expired() throws Exception {
        AuthState state = AuthState.random();
        when(stateCookieManager.read(any(), any())).thenReturn(Optional.of(state));
        doAnswer(inv -> { throw new OAuthFlowExpiredException("expired"); })
                .when(oAuthCallbackService).handle(any());

        mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                        .param("code", "x")
                        .param("state", state.value()))
                .andExpect(header().stringValues("Location",
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("OAUTH_FLOW_EXPIRED"))));
    }

    @Test
    void callback_redirects_with_provider_unavailable_when_service_throws_provider_call_failed() throws Exception {
        AuthState state = AuthState.random();
        when(stateCookieManager.read(any(), any())).thenReturn(Optional.of(state));
        doAnswer(inv -> { throw new OAuthProviderCallFailedException("token failed"); })
                .when(oAuthCallbackService).handle(any());

        mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                        .param("code", "x")
                        .param("state", state.value()))
                .andExpect(header().stringValues("Location",
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("OAUTH_PROVIDER_UNAVAILABLE"))));
    }

    @Test
    void callback_unsupported_provider_redirects_to_error_without_clearing_cookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/invalid/callback")
                        .param("code", "x")
                        .param("state", "y"))
                .andExpect(status().isFound())
                .andExpect(header().stringValues("Location",
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("OAUTH_AUTHORIZATION_FAILED"))));

        verify(stateCookieManager, never()).clear(any(), any());
    }
}
