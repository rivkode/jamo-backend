package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.identity.application.dto.AuthExchangeResult;
import app.backend.jamo.identity.application.dto.AuthRefreshCommand;
import app.backend.jamo.identity.application.service.AuthRefreshService;
import app.backend.jamo.identity.domain.exception.RefreshTokenExpiredException;
import app.backend.jamo.identity.domain.exception.RefreshTokenInvalidException;
import app.backend.jamo.identity.domain.exception.RefreshTokenReuseDetectedException;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.presentation.exception.AuthExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthRefreshController.class)
@Import(AuthExceptionHandler.class)
class AuthRefreshControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthRefreshService authRefreshService;

    @MockitoBean
    private JwtVerifier jwtVerifier;

    @Test
    void refresh_returns_200_with_new_jwt_pair_on_success() throws Exception {
        UserId userId = UserId.generate();
        when(authRefreshService.refresh(any(AuthRefreshCommand.class)))
                .thenReturn(new AuthExchangeResult(userId, "new-access", "new-refresh", 900L));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"valid-refresh-jwt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.asString()))
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900));
    }

    @Test
    void refresh_returns_401_refresh_expired_when_token_expired() throws Exception {
        doAnswer(inv -> { throw new RefreshTokenExpiredException("expired"); })
                .when(authRefreshService).refresh(any());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"expired-jwt\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_EXPIRED"));
    }

    @Test
    void refresh_returns_401_refresh_invalid_when_token_invalid() throws Exception {
        doAnswer(inv -> { throw new RefreshTokenInvalidException("forged"); })
                .when(authRefreshService).refresh(any());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"forged-jwt\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
    }

    @Test
    void refresh_returns_401_refresh_invalid_when_reuse_detected() throws Exception {
        // reuse detection 도 클라이언트엔 동일 REFRESH_INVALID — 보안 표준 (OWASP).
        // 응답 본문에 "REUSE" 신호 / sid raw value / 도메인 raw message 가 절대 노출되지 않아야 한다
        // (AuthExceptionHandler 의 reuse 통합 매핑 회귀 방어).
        doAnswer(inv -> { throw new RefreshTokenReuseDetectedException("reuse for sid=secret-sid-xyz"); })
                .when(authRefreshService).refresh(any());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"reused-jwt\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_INVALID"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("REUSE", "reuse", "secret-sid-xyz");
    }

    @Test
    void refresh_returns_400_when_refresh_token_blank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void refresh_returns_400_when_field_missing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
