package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.identity.application.dto.RegisterUserCommand;
import app.backend.jamo.identity.application.dto.RegisterUserResult;
import app.backend.jamo.identity.application.service.RegisterUserService;
import app.backend.jamo.identity.domain.exception.EmailAlreadyRegisteredException;
import app.backend.jamo.identity.domain.exception.EmailNotValidatedException;
import app.backend.jamo.identity.presentation.exception.UserExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserRegistrationController.class)
@Import(UserExceptionHandler.class)
class UserRegistrationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RegisterUserService registerUserService;
    // IdentityServiceConfig 가 KeyProvider/JwtVerifier 빈을 요구 — 본 endpoint 는 익명이지만
    // WebMvcTest slice 컨텍스트 시동을 위해 필요한 placeholder (test review L2).
    @MockitoBean private JwtVerifier jwtVerifier;

    private static final String VALID_BODY = "{\"email\":\"user@jamoai.app\","
            + "\"password\":\"PlainPa$$w0rd\","
            + "\"displayName\":\"jamo\"}";

    @Test
    void register_returns_201_with_response_body_on_happy_path() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-04-28T10:00:00Z");
        when(registerUserService.register(any(RegisterUserCommand.class)))
                .thenReturn(new RegisterUserResult(userId, "user@jamoai.app", "jamo", createdAt));

        ArgumentCaptor<RegisterUserCommand> captor = ArgumentCaptor.forClass(RegisterUserCommand.class);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("user@jamoai.app"))
                .andExpect(jsonPath("$.displayName").value("jamo"))
                .andExpect(jsonPath("$.createdAt").value("2026-04-28T10:00:00Z"));

        verify(registerUserService).register(captor.capture());
        RegisterUserCommand cmd = captor.getValue();
        assertThat(cmd.email()).isEqualTo("user@jamoai.app");
        assertThat(cmd.rawPassword()).isEqualTo("PlainPa$$w0rd");
        assertThat(cmd.displayName()).isEqualTo("jamo");
    }

    @Test
    void register_returns_400_EMAIL_NOT_VALIDATED_when_flag_missing() throws Exception {
        when(registerUserService.register(any(RegisterUserCommand.class)))
                .thenThrow(new EmailNotValidatedException("flag missing"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VALIDATED"));
    }

    @Test
    void register_returns_409_EMAIL_ALREADY_REGISTERED_when_local_account_duplicates() throws Exception {
        when(registerUserService.register(any(RegisterUserCommand.class)))
                .thenThrow(new EmailAlreadyRegisteredException("duplicate"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void register_returns_400_VALIDATION_FAILED_when_email_blank() throws Exception {
        String body = "{\"email\":\"\",\"password\":\"PlainPa$$w0rd\",\"displayName\":\"jamo\"}";

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(registerUserService, never()).register(any());
    }

    @Test
    void register_returns_400_VALIDATION_FAILED_when_email_invalid_format() throws Exception {
        String body = "{\"email\":\"not-an-email\",\"password\":\"PlainPa$$w0rd\",\"displayName\":\"jamo\"}";

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(registerUserService, never()).register(any());
    }

    @Test
    void register_returns_400_VALIDATION_FAILED_when_password_too_short() throws Exception {
        String body = "{\"email\":\"user@jamoai.app\",\"password\":\"short\",\"displayName\":\"jamo\"}";

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(registerUserService, never()).register(any());
    }

    @Test
    void register_returns_400_VALIDATION_FAILED_when_password_too_long() throws Exception {
        String tooLong = "P".repeat(73);
        String body = "{\"email\":\"user@jamoai.app\",\"password\":\"" + tooLong + "\",\"displayName\":\"jamo\"}";

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(registerUserService, never()).register(any());
    }

    @Test
    void register_returns_400_VALIDATION_FAILED_when_display_name_blank() throws Exception {
        String body = "{\"email\":\"user@jamoai.app\",\"password\":\"PlainPa$$w0rd\",\"displayName\":\"\"}";

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(registerUserService, never()).register(any());
    }

    @Test
    void register_returns_400_VALIDATION_FAILED_when_display_name_too_long() throws Exception {
        String tooLong = "j".repeat(33);
        String body = "{\"email\":\"user@jamoai.app\",\"password\":\"PlainPa$$w0rd\",\"displayName\":\"" + tooLong + "\"}";

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(registerUserService, never()).register(any());
    }

    @Test
    void register_returns_400_VALIDATION_FAILED_when_body_malformed() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void register_returns_400_VALIDATION_FAILED_when_password_field_missing() throws Exception {
        String body = "{\"email\":\"user@jamoai.app\",\"displayName\":\"jamo\"}";

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(registerUserService, never()).register(any());
    }

    @Test
    void register_returns_400_VALIDATION_FAILED_when_domain_iae_thrown_with_sanitized_message() throws Exception {
        // 도메인 IAE (예: HashedPassword blank) 가 IllegalArgumentException handler 에서 잡혀
        // raw 메시지가 응답에 노출되지 않는지 확인 (defense in depth, test review M1).
        when(registerUserService.register(any(RegisterUserCommand.class)))
                .thenThrow(new IllegalArgumentException("hashed password must not be blank"));

        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("request body is invalid"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("PlainPa$$w0rd")
                .doesNotContain("hashed password must not be blank");
    }

    @Test
    void register_returns_500_INTERNAL_ERROR_with_sanitized_message_on_unexpected_runtime_exception() throws Exception {
        // generic handler path — IAE 외 RuntimeException 이 새는 시나리오 (안전망 검증).
        when(registerUserService.register(any(RegisterUserCommand.class)))
                .thenThrow(new IllegalStateException("LOCAL user must have email"));

        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("internal server error"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("PlainPa$$w0rd")
                .doesNotContain("LOCAL user must have email");
    }
}
