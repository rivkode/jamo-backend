package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.identity.application.dto.AuthExchangeResult;
import app.backend.jamo.identity.application.dto.AuthLoginCommand;
import app.backend.jamo.identity.application.service.AuthLoginService;
import app.backend.jamo.identity.domain.exception.LoginInvalidException;
import app.backend.jamo.identity.domain.exception.LoginRateLimitedException;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.presentation.exception.AuthExceptionHandler;
import app.backend.jamo.identity.presentation.web.DeviceIdResolver;
import app.backend.jamo.identity.presentation.web.DeviceIdResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthLoginController.class)
@Import(AuthExceptionHandler.class)
class AuthLoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthLoginService authLoginService;

    @MockitoBean
    private DeviceIdResolver deviceIdResolver;

    @MockitoBean
    private JwtVerifier jwtVerifier;

    @Test
    void login_returns_200_with_jwt_pair_on_success() throws Exception {
        UserId userId = UserId.generate();
        when(deviceIdResolver.resolve(any())).thenReturn(new DeviceIdResult("device-1234", false));
        when(authLoginService.login(any(AuthLoginCommand.class)))
                .thenReturn(new AuthExchangeResult(userId, "access-jwt", "refresh-jwt", 900L));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"plain-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.asString()))
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-jwt"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900));

        verify(authLoginService).login(new AuthLoginCommand(
                "user@example.com", "plain-password", "device-1234", "127.0.0.1"));
    }

    @Test
    void login_returns_429_when_rate_limited() throws Exception {
        when(deviceIdResolver.resolve(any())).thenReturn(new DeviceIdResult("device-1234", false));
        doAnswer(inv -> { throw new LoginRateLimitedException("SECRET attempts=5"); })
                .when(authLoginService).login(any());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"plain-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("SECRET", "attempts");
    }

    @Test
    void login_sets_device_cookie_when_device_id_is_newly_generated() throws Exception {
        UserId userId = UserId.generate();
        when(deviceIdResolver.resolve(any())).thenReturn(new DeviceIdResult("web-generated-1234", true));
        doAnswer(inv -> {
            inv.getArgument(0, jakarta.servlet.http.HttpServletResponse.class)
                    .addHeader(HttpHeaders.SET_COOKIE, "jamo_device_id=web-generated-1234; Path=/; HttpOnly");
            return null;
        }).when(deviceIdResolver).setDeviceCookie(any(), any());
        when(authLoginService.login(any(AuthLoginCommand.class)))
                .thenReturn(new AuthExchangeResult(userId, "access-jwt", "refresh-jwt", 900L));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"plain-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("jamo_device_id=web-generated-1234");
        verify(deviceIdResolver).setDeviceCookie(any(), org.mockito.ArgumentMatchers.eq("web-generated-1234"));
    }

    @Test
    void login_returns_401_login_invalid_without_raw_message() throws Exception {
        when(deviceIdResolver.resolve(any())).thenReturn(new DeviceIdResult("device-1234", false));
        doAnswer(inv -> { throw new LoginInvalidException("SECRET user missing or password mismatch"); })
                .when(authLoginService).login(any());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_INVALID"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("SECRET");
        assertThat(body).doesNotContain("missing");
        assertThat(body).doesNotContain("mismatch");
    }

    @Test
    void login_returns_400_when_email_is_invalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-email\",\"password\":\"plain-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(authLoginService, never()).login(any());
    }

    @Test
    void login_returns_400_when_domain_email_validation_rejects() throws Exception {
        when(deviceIdResolver.resolve(any())).thenReturn(new DeviceIdResult("device-1234", false));
        doAnswer(inv -> { throw new IllegalArgumentException("invalid email format"); })
                .when(authLoginService).login(any());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"foo@bar\",\"password\":\"plain-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void login_returns_400_when_password_is_blank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(authLoginService, never()).login(any());
    }

    @Test
    void login_returns_400_when_email_is_blank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"plain-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(authLoginService, never()).login(any());
    }

    @Test
    void login_returns_400_when_password_is_missing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(authLoginService, never()).login(any());
    }

    @Test
    void login_returns_400_when_body_is_malformed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(authLoginService, never()).login(any());
    }
}
