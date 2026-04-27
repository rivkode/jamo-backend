package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.identity.application.dto.AuthExchangeCommand;
import app.backend.jamo.identity.application.dto.AuthExchangeResult;
import app.backend.jamo.identity.application.service.AuthExchangeService;
import app.backend.jamo.identity.domain.exception.AuthCodeExpiredException;
import app.backend.jamo.identity.domain.exception.AuthCodeNotFoundException;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.presentation.exception.AuthExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthExchangeController.class)
@Import(AuthExceptionHandler.class)
class AuthExchangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthExchangeService authExchangeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exchange_returns_200_with_jwt_pair_on_success() throws Exception {
        UserId userId = UserId.generate();
        when(authExchangeService.exchange(any(AuthExchangeCommand.class)))
                .thenReturn(new AuthExchangeResult(userId, "access-jwt", "refresh-jwt", 900L));

        mockMvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"authcode-x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.asString()))
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-jwt"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900));
    }

    @Test
    void exchange_returns_401_auth_code_invalid_when_not_found() throws Exception {
        doAnswer(inv -> { throw new AuthCodeNotFoundException("not found"); })
                .when(authExchangeService).exchange(any());

        mockMvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"missing\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_CODE_INVALID"));
    }

    @Test
    void exchange_returns_401_auth_code_invalid_when_expired() throws Exception {
        doAnswer(inv -> { throw new AuthCodeExpiredException("expired"); })
                .when(authExchangeService).exchange(any());

        mockMvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"expired\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_CODE_INVALID"));
    }

    @Test
    void exchange_returns_400_when_code_is_blank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void exchange_returns_400_when_body_malformed_json() throws Exception {
        mockMvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void exchange_returns_400_when_code_field_missing() throws Exception {
        // MethodArgumentNotValidException → handleValidation 경로 검증 (test review M3/M4)
        mockMvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void exchange_response_does_not_leak_provider_or_pepper() throws Exception {
        UserId userId = UserId.generate();
        when(authExchangeService.exchange(any()))
                .thenReturn(new AuthExchangeResult(userId, "access", "refresh", 900L));

        var result = mockMvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"x\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // 응답 스키마는 정확히 4 필드 (userId/accessToken/refreshToken/expiresInSeconds) 만 노출
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("pepper");
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("provider");
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("sessionId");
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("deviceId");
    }
}
