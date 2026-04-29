package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerificationException;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.diary.application.dto.diary.ToggleDiaryLikeCommand;
import app.backend.jamo.diary.application.dto.diary.ToggleDiaryLikeView;
import app.backend.jamo.diary.application.service.diary.ToggleDiaryLikeService;
import app.backend.jamo.diary.domain.exception.DiaryAccessDeniedException;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.presentation.exception.DiaryExceptionHandler;
import app.backend.jamo.diary.presentation.web.LoginUserArgumentResolver;
import app.backend.jamo.diary.presentation.web.PresentationWebConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

@WebMvcTest(controllers = DiaryLikeController.class)
@Import({DiaryExceptionHandler.class, LoginUserArgumentResolver.class, PresentationWebConfig.class})
class DiaryLikeControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DIARY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String SID = "sid-1";
    private static final String DEVICE_ID = "device-1";
    private static final Instant NOW = Instant.parse("2026-04-29T10:00:00Z");
    private static final String BEARER = "Bearer valid-access";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ToggleDiaryLikeService toggleDiaryLikeService;
    @MockitoBean private JwtVerifier jwtVerifier;

    private void mockValidAuth() throws JwtVerificationException {
        when(jwtVerifier.verify("valid-access")).thenReturn(new JwtClaims(
            USER_ID.toString(), SID, DEVICE_ID,
            JwtTokenType.ACCESS, NOW, NOW.plusSeconds(900)
        ));
    }

    @Test
    void toggle_returns_200_with_likeCount_5_when_liked_true() throws Exception {
        mockValidAuth();
        when(toggleDiaryLikeService.toggle(any()))
            .thenReturn(new ToggleDiaryLikeView(DIARY_ID, true, 5));

        mockMvc.perform(post("/api/v1/diaries/{id}/like", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"liked":true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.diaryId").value(DIARY_ID.toString()))
            .andExpect(jsonPath("$.liked").value(true))
            .andExpect(jsonPath("$.likeCount").value(5));

        ArgumentCaptor<ToggleDiaryLikeCommand> captor = ArgumentCaptor.forClass(ToggleDiaryLikeCommand.class);
        verify(toggleDiaryLikeService).toggle(captor.capture());
        ToggleDiaryLikeCommand cmd = captor.getValue();
        assertThat(cmd.diaryId()).isEqualTo(DIARY_ID);
        assertThat(cmd.userId()).isEqualTo(USER_ID);
        assertThat(cmd.liked()).isTrue();
    }

    @Test
    void toggle_returns_200_with_decremented_likeCount_when_liked_false() throws Exception {
        mockValidAuth();
        when(toggleDiaryLikeService.toggle(any()))
            .thenReturn(new ToggleDiaryLikeView(DIARY_ID, false, 4));

        mockMvc.perform(post("/api/v1/diaries/{id}/like", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"liked":false}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.liked").value(false))
            .andExpect(jsonPath("$.likeCount").value(4));
    }

    @Test
    void toggle_returns_404_when_NotFound_or_private_non_owner_IDOR() throws Exception {
        mockValidAuth();
        when(toggleDiaryLikeService.toggle(any()))
            .thenThrow(new DiaryNotFoundException("not found"));

        mockMvc.perform(post("/api/v1/diaries/{id}/like", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"liked":true}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("DIARY_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("diary not found"));
    }

    @Test
    void toggle_returns_404_when_AccessDenied_IDOR() throws Exception {
        // test-reviewer M1 — DiaryAccessDeniedException 도 NotFound 와 동일 응답 (404 통일 IDOR).
        mockValidAuth();
        when(toggleDiaryLikeService.toggle(any()))
            .thenThrow(new DiaryAccessDeniedException("not owner of private diary"));

        mockMvc.perform(post("/api/v1/diaries/{id}/like", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"liked":true}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("DIARY_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("diary not found"));
    }

    @Test
    void toggle_returns_400_when_path_diaryId_not_uuid() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/diaries/not-uuid/like")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"liked":true}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"));
        verify(toggleDiaryLikeService, never()).toggle(any());
    }

    @Test
    void toggle_returns_400_when_liked_field_missing() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/diaries/{id}/like", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"));
        verify(toggleDiaryLikeService, never()).toggle(any());
    }

    @Test
    void toggle_returns_400_when_body_malformed() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/diaries/{id}/like", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("not-json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"));
        verify(toggleDiaryLikeService, never()).toggle(any());
    }

    @Test
    void toggle_returns_401_when_no_auth_header_without_leaking_internal_message() throws Exception {
        mockMvc.perform(post("/api/v1/diaries/{id}/like", DIARY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"liked":true}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("authentication required"));
        verify(toggleDiaryLikeService, never()).toggle(any());
    }

    @Test
    void toggle_returns_401_when_jwt_verify_fails_without_leaking_cause() throws Exception {
        // 누락 가능성 4 — sanitization 회귀 신호 대칭 (DiaryControllerWebMvcTest 와 동일 정책).
        when(jwtVerifier.verify("bad-token"))
            .thenThrow(new JwtVerificationException("token expired at 2026-04-29"));

        mockMvc.perform(post("/api/v1/diaries/{id}/like", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"liked":true}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            // sanitize — JwtVerificationException raw message 비노출
            .andExpect(jsonPath("$.message").value("authentication required"));
        verify(toggleDiaryLikeService, never()).toggle(any());
    }
}
