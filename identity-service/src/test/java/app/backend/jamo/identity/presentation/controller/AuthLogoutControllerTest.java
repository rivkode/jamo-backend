package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtExpiredException;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerificationException;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.identity.application.dto.AuthLogoutCommand;
import app.backend.jamo.identity.application.service.AuthLogoutService;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.presentation.exception.AuthExceptionHandler;
import app.backend.jamo.identity.presentation.web.LoginUserArgumentResolver;
import app.backend.jamo.identity.presentation.web.PresentationWebConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthLogoutController.class)
@Import({AuthExceptionHandler.class, LoginUserArgumentResolver.class, PresentationWebConfig.class})
class AuthLogoutControllerTest {

    private static final UserId USER_ID = UserId.generate();
    private static final String SID = "sid-current";
    private static final String DEVICE_ID = "device-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthLogoutService authLogoutService;

    @MockitoBean
    private JwtVerifier jwtVerifier;

    private JwtClaims accessClaims() {
        Instant now = Instant.parse("2026-04-27T10:00:00Z");
        return new JwtClaims(USER_ID.asString(), SID, DEVICE_ID,
                JwtTokenType.ACCESS, now, now.plusSeconds(900));
    }

    @Test
    void logout_returns_204_and_calls_service_with_extracted_user_context() throws Exception {
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<AuthLogoutCommand> captor = ArgumentCaptor.forClass(AuthLogoutCommand.class);
        verify(authLogoutService).logout(captor.capture());
        AuthLogoutCommand cmd = captor.getValue();
        assertThat(cmd.userId()).isEqualTo(USER_ID);
        assertThat(cmd.sessionId()).isEqualTo(SID);
    }

    @Test
    void logout_returns_401_unauthorized_when_authorization_header_missing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(authLogoutService, never()).logout(any());
    }

    @Test
    void logout_returns_401_unauthorized_when_bearer_prefix_missing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "valid-access"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(authLogoutService, never()).logout(any());
    }

    @Test
    void logout_returns_401_unauthorized_when_token_expired() throws Exception {
        when(jwtVerifier.verify("expired-access"))
                .thenThrow(new JwtExpiredException("token expired"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-access"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(authLogoutService, never()).logout(any());
    }

    @Test
    void logout_returns_401_unauthorized_when_signature_invalid() throws Exception {
        when(jwtVerifier.verify("forged-access"))
                .thenThrow(new JwtVerificationException("invalid signature"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer forged-access"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(authLogoutService, never()).logout(any());
    }

    @Test
    void logout_returns_401_unauthorized_when_token_type_is_refresh_not_access() throws Exception {
        Instant now = Instant.parse("2026-04-27T10:00:00Z");
        JwtClaims refreshClaims = new JwtClaims(USER_ID.asString(), SID, DEVICE_ID,
                JwtTokenType.REFRESH, now, now.plusSeconds(900));
        when(jwtVerifier.verify("refresh-as-access")).thenReturn(refreshClaims);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer refresh-as-access"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(authLogoutService, never()).logout(any());
    }
}
