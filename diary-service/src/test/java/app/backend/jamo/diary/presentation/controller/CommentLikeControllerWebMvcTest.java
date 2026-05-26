package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerificationException;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.diary.application.dto.comment.ToggleCommentLikeCommand;
import app.backend.jamo.diary.application.dto.comment.ToggleCommentLikeView;
import app.backend.jamo.diary.application.service.comment.ToggleCommentLikeService;
import app.backend.jamo.diary.domain.exception.CommentNotFoundException;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.presentation.exception.CommentExceptionHandler;
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

@WebMvcTest(controllers = CommentLikeController.class)
@Import({CommentExceptionHandler.class, LoginUserArgumentResolver.class, PresentationWebConfig.class})
class CommentLikeControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COMMENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String SID = "sid-1";
    private static final String DEVICE_ID = "device-1";
    private static final Instant NOW = Instant.parse("2026-05-26T10:00:00Z");
    private static final String BEARER = "Bearer valid-access";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ToggleCommentLikeService toggleCommentLikeService;
    @MockitoBean private JwtVerifier jwtVerifier;

    private void mockValidAuth() throws JwtVerificationException {
        when(jwtVerifier.verify("valid-access")).thenReturn(new JwtClaims(
            USER_ID.toString(), SID, DEVICE_ID,
            JwtTokenType.ACCESS, NOW, NOW.plusSeconds(900)
        ));
    }

    @Test
    void toggle_returns_200_with_userLiked_and_likeCount() throws Exception {
        mockValidAuth();
        when(toggleCommentLikeService.toggle(any()))
            .thenReturn(new ToggleCommentLikeView(COMMENT_ID, true, 3));

        mockMvc.perform(post("/api/v1/comments/{commentId}/like", COMMENT_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"liked":true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commentId").value(COMMENT_ID.toString()))
            .andExpect(jsonPath("$.userLiked").value(true))
            .andExpect(jsonPath("$.likeCount").value(3));

        ArgumentCaptor<ToggleCommentLikeCommand> captor = ArgumentCaptor.forClass(ToggleCommentLikeCommand.class);
        verify(toggleCommentLikeService).toggle(captor.capture());
        ToggleCommentLikeCommand cmd = captor.getValue();
        assertThat(cmd.commentId()).isEqualTo(COMMENT_ID);
        assertThat(cmd.viewerId()).isEqualTo(USER_ID);
        assertThat(cmd.liked()).isTrue();
    }

    @Test
    void toggle_returns_200_with_userLiked_false_when_unlike() throws Exception {
        mockValidAuth();
        when(toggleCommentLikeService.toggle(any()))
            .thenReturn(new ToggleCommentLikeView(COMMENT_ID, false, 2));

        mockMvc.perform(post("/api/v1/comments/{commentId}/like", COMMENT_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"liked":false}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userLiked").value(false))
            .andExpect(jsonPath("$.likeCount").value(2));
    }

    @Test
    void toggle_returns_404_when_comment_or_diary_not_found_IDOR() throws Exception {
        mockValidAuth();
        when(toggleCommentLikeService.toggle(any()))
            .thenThrow(new CommentNotFoundException("not found"));

        mockMvc.perform(post("/api/v1/comments/{commentId}/like", COMMENT_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"liked":true}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    void toggle_returns_404_when_diary_inaccessible() throws Exception {
        mockValidAuth();
        when(toggleCommentLikeService.toggle(any()))
            .thenThrow(new DiaryNotFoundException("hidden"));

        mockMvc.perform(post("/api/v1/comments/{commentId}/like", COMMENT_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"liked":true}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    void toggle_returns_400_when_liked_field_missing() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/comments/{commentId}/like", COMMENT_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMENT_VALIDATION_FAILED"));
        verify(toggleCommentLikeService, never()).toggle(any());
    }

    @Test
    void toggle_returns_400_when_path_commentId_not_uuid() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/comments/not-uuid/like")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"liked":true}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMENT_VALIDATION_FAILED"));
        verify(toggleCommentLikeService, never()).toggle(any());
    }

    @Test
    void toggle_returns_401_when_no_auth_header() throws Exception {
        mockMvc.perform(post("/api/v1/comments/{commentId}/like", COMMENT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"liked":true}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("authentication required"));
        verify(toggleCommentLikeService, never()).toggle(any());
    }

    @Test
    void toggle_returns_401_when_jwt_verify_fails_without_leaking_cause() throws Exception {
        // sanitization 회귀 신호 — JwtVerificationException raw cause 비노출 (DiaryLike 패턴 정합).
        when(jwtVerifier.verify("bad-token"))
            .thenThrow(new JwtVerificationException("token expired at 2026-05-26"));

        mockMvc.perform(post("/api/v1/comments/{commentId}/like", COMMENT_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"liked":true}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("authentication required"));
        verify(toggleCommentLikeService, never()).toggle(any());
    }
}
