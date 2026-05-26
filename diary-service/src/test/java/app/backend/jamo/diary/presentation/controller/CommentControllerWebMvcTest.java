package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerificationException;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.diary.application.cursor.InvalidCommentCursorException;
import app.backend.jamo.diary.application.dto.comment.CommentListView;
import app.backend.jamo.diary.application.dto.comment.CommentView;
import app.backend.jamo.diary.application.dto.comment.CreateCommentCommand;
import app.backend.jamo.diary.application.dto.comment.DeleteCommentCommand;
import app.backend.jamo.diary.application.dto.comment.ListCommentsQuery;
import app.backend.jamo.diary.application.service.comment.CreateCommentService;
import app.backend.jamo.diary.application.service.comment.DeleteCommentService;
import app.backend.jamo.diary.application.service.comment.ListCommentsService;
import app.backend.jamo.diary.domain.exception.CommentAccessDeniedException;
import app.backend.jamo.diary.domain.exception.CommentNotFoundException;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.exception.InvalidCommentContentException;
import app.backend.jamo.diary.domain.exception.InvalidCommentParentException;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CommentController.class)
@Import({CommentExceptionHandler.class, LoginUserArgumentResolver.class, PresentationWebConfig.class})
class CommentControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DIARY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID COMMENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PARENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String SID = "sid-1";
    private static final String DEVICE_ID = "device-1";
    private static final Instant NOW = Instant.parse("2026-05-26T10:00:00Z");
    private static final String BEARER = "Bearer valid-access";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CreateCommentService createCommentService;
    @MockitoBean private ListCommentsService listCommentsService;
    @MockitoBean private DeleteCommentService deleteCommentService;
    @MockitoBean private JwtVerifier jwtVerifier;

    private void mockValidAuth() throws JwtVerificationException {
        when(jwtVerifier.verify("valid-access")).thenReturn(new JwtClaims(
            USER_ID.toString(), SID, DEVICE_ID,
            JwtTokenType.ACCESS, NOW, NOW.plusSeconds(900)
        ));
    }

    private CommentView sampleView(UUID parentId) {
        return new CommentView(
            COMMENT_ID, DIARY_ID, USER_ID,
            "Minji", "hello world", parentId,
            0, false, NOW
        );
    }

    @Test
    void create_returns_201_with_comment_body() throws Exception {
        mockValidAuth();
        when(createCommentService.create(any())).thenReturn(sampleView(null));

        mockMvc.perform(post("/api/v1/diaries/{diaryId}/comments", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"text":"hello world","parentCommentId":null}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.commentId").value(COMMENT_ID.toString()))
            .andExpect(jsonPath("$.diaryId").value(DIARY_ID.toString()))
            .andExpect(jsonPath("$.author.userId").value(USER_ID.toString()))
            .andExpect(jsonPath("$.author.username").value("Minji"))
            // PRD §3: "avatarUrl": null 명시 노출 — Jackson Include.ALWAYS 정책 (CommentAuthor javadoc 박제)
            .andExpect(jsonPath("$.author.avatarUrl").isEmpty())
            .andExpect(jsonPath("$.text").value("hello world"))
            // PRD §3: "parentCommentId": null 명시 노출 — 루트 댓글
            .andExpect(jsonPath("$.parentCommentId").isEmpty())
            .andExpect(jsonPath("$.likeCount").value(0))
            .andExpect(jsonPath("$.userLiked").value(false));

        ArgumentCaptor<CreateCommentCommand> captor = ArgumentCaptor.forClass(CreateCommentCommand.class);
        verify(createCommentService).create(captor.capture());
        CreateCommentCommand cmd = captor.getValue();
        assertThat(cmd.diaryId()).isEqualTo(DIARY_ID);
        assertThat(cmd.authorId()).isEqualTo(USER_ID);
        assertThat(cmd.content()).isEqualTo("hello world");
        assertThat(cmd.parentIdOrNull()).isNull();
    }

    @Test
    void create_parses_parentCommentId_when_present() throws Exception {
        mockValidAuth();
        when(createCommentService.create(any())).thenReturn(sampleView(PARENT_ID));

        mockMvc.perform(post("/api/v1/diaries/{diaryId}/comments", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"text":"reply","parentCommentId":"%s"}
                    """.formatted(PARENT_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.parentCommentId").value(PARENT_ID.toString()));

        ArgumentCaptor<CreateCommentCommand> captor = ArgumentCaptor.forClass(CreateCommentCommand.class);
        verify(createCommentService).create(captor.capture());
        assertThat(captor.getValue().parentIdOrNull()).isEqualTo(PARENT_ID);
    }

    @Test
    void create_treats_blank_parentCommentId_as_root() throws Exception {
        // Controller 의 isBlank() 분기 명시 — 빈 문자열 / 공백은 null 동의어 (루트 댓글).
        mockValidAuth();
        when(createCommentService.create(any())).thenReturn(sampleView(null));

        mockMvc.perform(post("/api/v1/diaries/{diaryId}/comments", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"text":"hi","parentCommentId":""}
                    """))
            .andExpect(status().isCreated());

        ArgumentCaptor<CreateCommentCommand> captor = ArgumentCaptor.forClass(CreateCommentCommand.class);
        verify(createCommentService).create(captor.capture());
        assertThat(captor.getValue().parentIdOrNull()).isNull();
    }

    @Test
    void create_returns_404_when_diary_not_accessible() throws Exception {
        mockValidAuth();
        when(createCommentService.create(any()))
            .thenThrow(new DiaryNotFoundException("hidden"));

        mockMvc.perform(post("/api/v1/diaries/{diaryId}/comments", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"text":"hi"}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    void create_returns_400_when_parent_must_be_root() throws Exception {
        mockValidAuth();
        when(createCommentService.create(any()))
            .thenThrow(new InvalidCommentParentException("parent_must_be_root"));

        mockMvc.perform(post("/api/v1/diaries/{diaryId}/comments", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"text":"deep","parentCommentId":"%s"}
                    """.formatted(PARENT_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMENT_VALIDATION_FAILED"));
    }

    @Test
    void create_returns_400_when_text_blank() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/diaries/{diaryId}/comments", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"text":"  "}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMENT_VALIDATION_FAILED"));
        verify(createCommentService, never()).create(any());
    }

    @Test
    void create_returns_400_when_text_VO_invariant_violated() throws Exception {
        // Bean Validation 통과해도 도메인 VO 가 다시 검증 (예: pure whitespace VO 차단).
        mockValidAuth();
        when(createCommentService.create(any()))
            .thenThrow(new InvalidCommentContentException("blank"));

        mockMvc.perform(post("/api/v1/diaries/{diaryId}/comments", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"text":"valid input"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMENT_VALIDATION_FAILED"));
    }

    @Test
    void create_returns_400_when_text_exceeds_1000_char_first_pass_limit() throws Exception {
        // Bean Validation 1차 거부 한도 — CreateCommentRequest.TEXT_MAX_CHARS=1000.
        mockValidAuth();
        String tooLong = "a".repeat(1001);

        mockMvc.perform(post("/api/v1/diaries/{diaryId}/comments", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"text":"%s"}
                    """.formatted(tooLong)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMENT_VALIDATION_FAILED"));
        verify(createCommentService, never()).create(any());
    }

    @Test
    void create_returns_400_when_path_diaryId_not_uuid() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/diaries/not-uuid/comments")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"text":"hi"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMENT_VALIDATION_FAILED"));
        verify(createCommentService, never()).create(any());
    }

    @Test
    void create_returns_400_when_parentCommentId_not_uuid() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/diaries/{diaryId}/comments", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"text":"hi","parentCommentId":"not-uuid"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMENT_VALIDATION_FAILED"));
        verify(createCommentService, never()).create(any());
    }

    @Test
    void create_returns_401_when_no_auth_header() throws Exception {
        mockMvc.perform(post("/api/v1/diaries/{diaryId}/comments", DIARY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"text":"hi"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(createCommentService, never()).create(any());
    }

    @Test
    void list_returns_200_with_items_and_paging() throws Exception {
        mockValidAuth();
        when(listCommentsService.list(any())).thenReturn(new CommentListView(
            List.of(sampleView(null)), "next-cursor", true
        ));

        mockMvc.perform(get("/api/v1/diaries/{diaryId}/comments", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .param("cursor", "c1")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].commentId").value(COMMENT_ID.toString()))
            .andExpect(jsonPath("$.items[0].text").value("hello world"))
            .andExpect(jsonPath("$.nextCursor").value("next-cursor"))
            .andExpect(jsonPath("$.hasNext").value(true));

        ArgumentCaptor<ListCommentsQuery> captor = ArgumentCaptor.forClass(ListCommentsQuery.class);
        verify(listCommentsService).list(captor.capture());
        ListCommentsQuery q = captor.getValue();
        assertThat(q.diaryId()).isEqualTo(DIARY_ID);
        assertThat(q.viewerId()).isEqualTo(USER_ID);
        assertThat(q.cursorOrNull()).isEqualTo("c1");
        assertThat(q.size()).isEqualTo(10);
    }

    @Test
    void list_defaults_size_to_20_when_omitted() throws Exception {
        mockValidAuth();
        when(listCommentsService.list(any())).thenReturn(new CommentListView(List.of(), null, false));

        mockMvc.perform(get("/api/v1/diaries/{diaryId}/comments", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.hasNext").value(false));

        ArgumentCaptor<ListCommentsQuery> captor = ArgumentCaptor.forClass(ListCommentsQuery.class);
        verify(listCommentsService).list(captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(20);
    }

    @Test
    void list_returns_400_when_size_below_min() throws Exception {
        mockValidAuth();

        mockMvc.perform(get("/api/v1/diaries/{diaryId}/comments", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .param("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMENT_VALIDATION_FAILED"));
        verify(listCommentsService, never()).list(any());
    }

    @Test
    void list_returns_400_when_size_above_max() throws Exception {
        // @Max(100) upper boundary 회귀 신호.
        mockValidAuth();

        mockMvc.perform(get("/api/v1/diaries/{diaryId}/comments", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMENT_VALIDATION_FAILED"));
        verify(listCommentsService, never()).list(any());
    }

    @Test
    void list_returns_400_when_cursor_invalid() throws Exception {
        mockValidAuth();
        when(listCommentsService.list(any()))
            .thenThrow(new InvalidCommentCursorException("malformed"));

        mockMvc.perform(get("/api/v1/diaries/{diaryId}/comments", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .param("cursor", "broken"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMENT_VALIDATION_FAILED"));
    }

    @Test
    void list_returns_404_when_diary_not_accessible() throws Exception {
        mockValidAuth();
        when(listCommentsService.list(any()))
            .thenThrow(new DiaryNotFoundException("hidden"));

        mockMvc.perform(get("/api/v1/diaries/{diaryId}/comments", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    void delete_returns_204() throws Exception {
        mockValidAuth();

        mockMvc.perform(delete("/api/v1/comments/{commentId}", COMMENT_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isNoContent());

        ArgumentCaptor<DeleteCommentCommand> captor = ArgumentCaptor.forClass(DeleteCommentCommand.class);
        verify(deleteCommentService).delete(captor.capture());
        DeleteCommentCommand cmd = captor.getValue();
        assertThat(cmd.commentId()).isEqualTo(COMMENT_ID);
        assertThat(cmd.requesterId()).isEqualTo(USER_ID);
    }

    @Test
    void delete_returns_404_when_not_owner_or_missing_IDOR() throws Exception {
        mockValidAuth();
        doThrow(new CommentNotFoundException("not found"))
            .when(deleteCommentService).delete(any());

        mockMvc.perform(delete("/api/v1/comments/{commentId}", COMMENT_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    void delete_returns_404_when_CommentAccessDenied_IDOR() throws Exception {
        // CommentAccessDeniedException 도 NotFound 와 동일 매핑 (404 통일 IDOR, 박제 §4).
        mockValidAuth();
        doThrow(new CommentAccessDeniedException("not owner"))
            .when(deleteCommentService).delete(any());

        mockMvc.perform(delete("/api/v1/comments/{commentId}", COMMENT_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    void delete_returns_401_when_no_auth_header() throws Exception {
        mockMvc.perform(delete("/api/v1/comments/{commentId}", COMMENT_ID))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(deleteCommentService, never()).delete(any());
    }

    @Test
    void delete_returns_400_when_path_commentId_not_uuid() throws Exception {
        mockValidAuth();

        mockMvc.perform(delete("/api/v1/comments/not-uuid")
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMENT_VALIDATION_FAILED"));
        verify(deleteCommentService, never()).delete(any());
    }

    @Test
    void delete_returns_401_when_jwt_verify_fails_without_leaking_cause() throws Exception {
        when(jwtVerifier.verify("bad-token"))
            .thenThrow(new JwtVerificationException("token expired"));

        mockMvc.perform(delete("/api/v1/comments/{commentId}", COMMENT_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("authentication required"));
        verify(deleteCommentService, never()).delete(any());
    }
}
