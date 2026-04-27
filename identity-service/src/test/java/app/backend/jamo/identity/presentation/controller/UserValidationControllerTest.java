package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.identity.application.dto.SendValidationCodeCommand;
import app.backend.jamo.identity.application.dto.VerifyValidationCodeCommand;
import app.backend.jamo.identity.application.service.SendValidationCodeService;
import app.backend.jamo.identity.application.service.VerifyValidationCodeService;
import app.backend.jamo.identity.domain.exception.ValidationCodeExpiredException;
import app.backend.jamo.identity.domain.exception.ValidationCodeLockedException;
import app.backend.jamo.identity.domain.exception.ValidationCodeMismatchException;
import app.backend.jamo.identity.domain.exception.ValidationRateLimitedException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserValidationController.class)
@Import(UserExceptionHandler.class)
class UserValidationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SendValidationCodeService sendValidationCodeService;
    @MockitoBean private VerifyValidationCodeService verifyValidationCodeService;
    @MockitoBean private JwtVerifier jwtVerifier;

    @Test
    void send_returns_204_with_normalized_command() throws Exception {
        ArgumentCaptor<SendValidationCodeCommand> captor =
                ArgumentCaptor.forClass(SendValidationCodeCommand.class);

        mockMvc.perform(post("/api/v1/users/validation-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\"}"))
                .andExpect(status().isNoContent());

        verify(sendValidationCodeService).send(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("test@example.com");
    }

    @Test
    void send_returns_400_VALIDATION_FAILED_when_email_blank() throws Exception {
        mockMvc.perform(post("/api/v1/users/validation-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void send_returns_400_VALIDATION_FAILED_when_email_format_invalid() throws Exception {
        mockMvc.perform(post("/api/v1/users/validation-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void send_returns_400_VALIDATION_FAILED_when_field_missing() throws Exception {
        mockMvc.perform(post("/api/v1/users/validation-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void send_returns_429_VALIDATION_RATE_LIMITED_when_service_throws() throws Exception {
        doAnswer(inv -> { throw new ValidationRateLimitedException("rate exceeded"); })
                .when(sendValidationCodeService).send(any());

        mockMvc.perform(post("/api/v1/users/validation-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VALIDATION_RATE_LIMITED"));
    }

    @Test
    void verify_returns_204_with_normalized_command() throws Exception {
        ArgumentCaptor<VerifyValidationCodeCommand> captor =
                ArgumentCaptor.forClass(VerifyValidationCodeCommand.class);

        mockMvc.perform(post("/api/v1/users/validation-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isNoContent());

        verify(verifyValidationCodeService).verify(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("test@example.com");
        assertThat(captor.getValue().code()).isEqualTo("123456");
    }

    @Test
    void verify_returns_400_VALIDATION_FAILED_when_code_not_six_digits() throws Exception {
        mockMvc.perform(post("/api/v1/users/validation-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"code\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void verify_returns_400_VALIDATION_FAILED_when_code_contains_non_digit() throws Exception {
        mockMvc.perform(post("/api/v1/users/validation-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"code\":\"12345a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void verify_returns_400_VALIDATION_CODE_MISMATCH_when_service_throws_mismatch() throws Exception {
        doAnswer(inv -> { throw new ValidationCodeMismatchException("mismatch (attempt 2)"); })
                .when(verifyValidationCodeService).verify(any());

        MvcResult result = mockMvc.perform(post("/api/v1/users/validation-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"code\":\"654321\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_CODE_MISMATCH"))
                .andExpect(jsonPath("$.attempts").doesNotExist())
                .andReturn();

        // raw exception 메시지 (attempt 카운트 포함) 가 응답에 누출되지 않는지 확인 (security review H2)
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("attempt", "(attempt", "(2)");
    }

    @Test
    void verify_returns_400_VALIDATION_CODE_EXPIRED_when_service_throws_expired() throws Exception {
        doAnswer(inv -> { throw new ValidationCodeExpiredException("expired"); })
                .when(verifyValidationCodeService).verify(any());

        mockMvc.perform(post("/api/v1/users/validation-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"code\":\"654321\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_CODE_EXPIRED"));
    }

    @Test
    void verify_returns_400_VALIDATION_CODE_LOCKED_when_service_throws_locked() throws Exception {
        // 서비스가 raw message 에 attempts 횟수를 우연히 포함시켜도 응답에는 노출되면 안 됨
        doAnswer(inv -> { throw new ValidationCodeLockedException("locked after 5 failed attempts"); })
                .when(verifyValidationCodeService).verify(any());

        MvcResult result = mockMvc.perform(post("/api/v1/users/validation-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"code\":\"654321\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_CODE_LOCKED"))
                .andExpect(jsonPath("$.attempts").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("attempt", "failed", "after 5");
    }

    @Test
    void send_returns_400_VALIDATION_FAILED_when_email_passes_at_email_but_fails_domain_vo() throws Exception {
        // @Email (Hibernate lax) 통과: dot 없는 도메인 — Domain Email VO 거부 → IllegalArgumentException
        // 본 회귀 테스트 부재 시 IAE 가 generic Exception fallback 으로 흘러 500 으로 응답됨 (security H1).
        doAnswer(inv -> { throw new IllegalArgumentException("invalid email format"); })
                .when(sendValidationCodeService).send(any());

        mockMvc.perform(post("/api/v1/users/validation-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"foo@bar\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void verify_returns_400_VALIDATION_FAILED_when_request_body_malformed() throws Exception {
        mockMvc.perform(post("/api/v1/users/validation-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"code\":}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
