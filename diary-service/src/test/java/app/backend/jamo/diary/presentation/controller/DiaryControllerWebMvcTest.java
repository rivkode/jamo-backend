package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerificationException;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.diary.application.dto.diary.CreateDiaryCommand;
import app.backend.jamo.diary.application.dto.diary.DeleteDiaryCommand;
import app.backend.jamo.diary.application.dto.diary.DiaryView;
import app.backend.jamo.diary.application.dto.diary.FeedView;
import app.backend.jamo.diary.application.dto.diary.GetDiaryQuery;
import app.backend.jamo.diary.application.dto.diary.ListMyFeedQuery;
import app.backend.jamo.diary.application.dto.diary.ListPublicFeedQuery;
import app.backend.jamo.diary.application.service.diary.CreateDiaryService;
import app.backend.jamo.diary.application.service.diary.DeleteDiaryService;
import app.backend.jamo.diary.application.service.diary.GetDiaryService;
import app.backend.jamo.diary.application.service.diary.ListMyFeedService;
import app.backend.jamo.diary.application.service.diary.ListPublicFeedService;
import app.backend.jamo.diary.domain.exception.DiaryAccessDeniedException;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.exception.InvalidDiaryContentException;
import app.backend.jamo.diary.domain.exception.InvalidImageUrlException;
import app.backend.jamo.diary.domain.model.diary.DiaryFeedSort;
import app.backend.jamo.diary.domain.model.diary.Visibility;
import app.backend.jamo.diary.domain.repository.cursor.PopularFeedCursor;
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

@WebMvcTest(controllers = DiaryController.class)
@Import({DiaryExceptionHandler.class, LoginUserArgumentResolver.class, PresentationWebConfig.class})
class DiaryControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DIARY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String SID = "sid-1";
    private static final String DEVICE_ID = "device-1";
    private static final Instant NOW = Instant.parse("2026-04-29T10:00:00Z");
    private static final String BEARER = "Bearer valid-access";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CreateDiaryService createDiaryService;
    @MockitoBean private GetDiaryService getDiaryService;
    @MockitoBean private ListPublicFeedService listPublicFeedService;
    @MockitoBean private ListMyFeedService listMyFeedService;
    @MockitoBean private DeleteDiaryService deleteDiaryService;
    @MockitoBean private JwtVerifier jwtVerifier;

    private void mockValidAuth() throws JwtVerificationException {
        when(jwtVerifier.verify("valid-access")).thenReturn(new JwtClaims(
            USER_ID.toString(), SID, DEVICE_ID,
            JwtTokenType.ACCESS, NOW, NOW.plusSeconds(900)
        ));
    }

    private DiaryView publicDiaryView() {
        return new DiaryView(
            DIARY_ID, USER_ID, "철수",
            "오늘은 기분이 좋다", List.of("https://cdn.example/a.png"), List.of("일상"),
            Visibility.PUBLIC, 0, 0, false, NOW
        );
    }

    private DiaryView privateDiaryView() {
        return new DiaryView(
            DIARY_ID, USER_ID, "철수",
            "비공개 메모", List.of(), List.of(),
            Visibility.PRIVATE, 3, 1, true, NOW
        );
    }

    // ============================================================
    // POST /api/v1/diaries (create)
    // ============================================================

    @Test
    void create_returns_201_with_DiaryResponse_11_fields_and_default_PUBLIC_visibility() throws Exception {
        mockValidAuth();
        when(createDiaryService.create(any())).thenReturn(publicDiaryView());

        mockMvc.perform(post("/api/v1/diaries")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":"오늘은 기분이 좋다","images":["https://cdn.example/a.png"],"tags":["일상"]}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.diaryId").value(DIARY_ID.toString()))
            .andExpect(jsonPath("$.authorId").value(USER_ID.toString()))
            .andExpect(jsonPath("$.authorDisplayName").value("철수"))
            .andExpect(jsonPath("$.content").value("오늘은 기분이 좋다"))
            .andExpect(jsonPath("$.images[0]").value("https://cdn.example/a.png"))
            .andExpect(jsonPath("$.tags[0]").value("일상"))
            .andExpect(jsonPath("$.visibility").value("PUBLIC"))
            .andExpect(jsonPath("$.likeCount").value(0))
            .andExpect(jsonPath("$.commentCount").value(0))
            .andExpect(jsonPath("$.likedByMe").value(false))
            .andExpect(jsonPath("$.createdAt").exists());

        ArgumentCaptor<CreateDiaryCommand> captor = ArgumentCaptor.forClass(CreateDiaryCommand.class);
        verify(createDiaryService).create(captor.capture());
        CreateDiaryCommand cmd = captor.getValue();
        assertThat(cmd.authorId()).isEqualTo(USER_ID);
        assertThat(cmd.content()).isEqualTo("오늘은 기분이 좋다");
        assertThat(cmd.visibility()).isEqualTo(Visibility.PUBLIC);  // default 적용 박제 §3
    }

    @Test
    void create_passes_explicit_PRIVATE_visibility_to_command() throws Exception {
        mockValidAuth();
        when(createDiaryService.create(any())).thenReturn(privateDiaryView());

        mockMvc.perform(post("/api/v1/diaries")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":"비공개 메모","visibility":"PRIVATE"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.visibility").value("PRIVATE"));

        ArgumentCaptor<CreateDiaryCommand> captor = ArgumentCaptor.forClass(CreateDiaryCommand.class);
        verify(createDiaryService).create(captor.capture());
        assertThat(captor.getValue().visibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void create_returns_400_when_content_blank() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/diaries")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":"   "}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"));
        verify(createDiaryService, never()).create(any());
    }

    @Test
    void create_returns_400_when_tags_exceeds_10() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/diaries")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":"hi","tags":["a","b","c","d","e","f","g","h","i","j","k"]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"));
        verify(createDiaryService, never()).create(any());
    }

    @Test
    void create_returns_400_when_visibility_unknown_value() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/diaries")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":"hi","visibility":"FOLLOWERS_ONLY"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"));
        verify(createDiaryService, never()).create(any());
    }

    @Test
    void create_returns_400_when_image_url_has_invalid_scheme_via_domain_invariant() throws Exception {
        // test-reviewer M4 — InvalidImageUrlException → DiaryExceptionHandler.handleDomainValidation 매핑 회귀
        // 신호. Bean Validation 은 길이만 검증 — http(s) scheme 차단은 ImageUrls VO invariant.
        mockValidAuth();
        when(createDiaryService.create(any()))
            .thenThrow(new InvalidImageUrlException("scheme must be http or https"));

        mockMvc.perform(post("/api/v1/diaries")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":"hi","images":["ftp://example/a.png"]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"))
            // sanitize — 도메인 raw message ("scheme must be http or https") 비노출
            .andExpect(jsonPath("$.message").value("request is invalid"));
    }

    @Test
    void create_returns_400_when_content_violates_domain_codepoint_invariant() throws Exception {
        // test-reviewer M4 부가 — Bean Validation 통과한 char 길이가 도메인 cp 한도 위반 시
        // InvalidDiaryContentException 매핑 회귀 신호.
        mockValidAuth();
        when(createDiaryService.create(any()))
            .thenThrow(new InvalidDiaryContentException("length out of range: max 2000 cp"));

        mockMvc.perform(post("/api/v1/diaries")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":"hi"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.message").value("request is invalid"));
    }

    @Test
    void create_returns_400_when_body_malformed_json() throws Exception {
        mockValidAuth();

        mockMvc.perform(post("/api/v1/diaries")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("not-json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"));
        verify(createDiaryService, never()).create(any());
    }

    @Test
    void create_returns_401_when_no_auth_header_without_leaking_internal_message() throws Exception {
        mockMvc.perform(post("/api/v1/diaries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":"hi"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("authentication required"));
        verify(createDiaryService, never()).create(any());
    }

    @Test
    void create_returns_401_when_jwt_verify_fails_without_leaking_cause() throws Exception {
        when(jwtVerifier.verify("bad-token"))
            .thenThrow(new JwtVerificationException("token expired at 2026-04-29"));

        mockMvc.perform(post("/api/v1/diaries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":"hi"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            // sanitize — JwtVerificationException raw message ("token expired at 2026-04-29") 비노출
            .andExpect(jsonPath("$.message").value("authentication required"));
        verify(createDiaryService, never()).create(any());
    }

    // ============================================================
    // GET /api/v1/diaries/{diaryId} (get)
    // ============================================================

    @Test
    void get_returns_200_with_DiaryResponse() throws Exception {
        mockValidAuth();
        when(getDiaryService.get(any())).thenReturn(publicDiaryView());

        mockMvc.perform(get("/api/v1/diaries/{id}", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.diaryId").value(DIARY_ID.toString()))
            .andExpect(jsonPath("$.visibility").value("PUBLIC"));

        ArgumentCaptor<GetDiaryQuery> captor = ArgumentCaptor.forClass(GetDiaryQuery.class);
        verify(getDiaryService).get(captor.capture());
        assertThat(captor.getValue().diaryId()).isEqualTo(DIARY_ID);
        assertThat(captor.getValue().viewerId()).isEqualTo(USER_ID);
    }

    @Test
    void get_returns_404_when_NotFound_or_other_user_IDOR() throws Exception {
        mockValidAuth();
        when(getDiaryService.get(any()))
            .thenThrow(new DiaryNotFoundException("not found"));

        mockMvc.perform(get("/api/v1/diaries/{id}", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("DIARY_NOT_FOUND"))
            // sanitize — 도메인 raw message ("not found") 비노출
            .andExpect(jsonPath("$.message").value("diary not found"));
    }

    @Test
    void get_returns_404_when_AccessDenied_IDOR() throws Exception {
        // test-reviewer M1 — DiaryAccessDeniedException 도 NotFoundException 과 동일 응답으로 매핑됨을
        // 명시 검증. ExceptionHandler 의 묶음 매핑 (DiaryNotFoundException + DiaryAccessDeniedException →
        // 404 통일) 에서 AccessDenied 가 누락되는 회귀 차단.
        mockValidAuth();
        when(getDiaryService.get(any()))
            .thenThrow(new DiaryAccessDeniedException("not owner of private diary"));

        mockMvc.perform(get("/api/v1/diaries/{id}", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("DIARY_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("diary not found"));
    }

    @Test
    void get_returns_400_when_path_diaryId_not_uuid() throws Exception {
        mockValidAuth();

        mockMvc.perform(get("/api/v1/diaries/not-uuid")
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"));
        verify(getDiaryService, never()).get(any());
    }

    @Test
    void get_returns_401_when_no_auth_header() throws Exception {
        mockMvc.perform(get("/api/v1/diaries/{id}", DIARY_ID))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(getDiaryService, never()).get(any());
    }

    // ============================================================
    // GET /api/v1/diaries/feed (listFeed)
    // ============================================================

    @Test
    void feed_returns_200_with_default_RECENT_sort_when_no_query() throws Exception {
        mockValidAuth();
        when(listPublicFeedService.listPublicFeed(any()))
            .thenReturn(new FeedView(List.of(publicDiaryView()), null, false));

        mockMvc.perform(get("/api/v1/diaries/feed")
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].diaryId").value(DIARY_ID.toString()))
            .andExpect(jsonPath("$.items[0].visibility").value("PUBLIC"))
            .andExpect(jsonPath("$.hasNext").value(false))
            .andExpect(jsonPath("$.nextCursor").doesNotExist());

        ArgumentCaptor<ListPublicFeedQuery> captor = ArgumentCaptor.forClass(ListPublicFeedQuery.class);
        verify(listPublicFeedService).listPublicFeed(captor.capture());
        ListPublicFeedQuery q = captor.getValue();
        assertThat(q.viewerId()).isEqualTo(USER_ID);
        assertThat(q.sort()).isEqualTo(DiaryFeedSort.RECENT);
        assertThat(q.size()).isEqualTo(10);
        assertThat(q.tag()).isEmpty();
        assertThat(q.recentCursor()).isEmpty();
        assertThat(q.popularCursor()).isEmpty();
    }

    @Test
    void feed_decodes_POPULAR_cursor_into_popularCursor_only_with_recentCursor_empty() throws Exception {
        // test-reviewer M2 — sort 별 cursor 분기 (decodeRecent vs decodePopular) 의 정확한 슬롯 매핑
        // 회귀 신호. 누군가 popular/recent 분기 swap 시 본 테스트가 잡음.
        mockValidAuth();
        when(listPublicFeedService.listPublicFeed(any()))
            .thenReturn(new FeedView(List.of(), null, false));

        // P|<likeCount>|<createdAtIso>|<diaryId> base64 url-safe (no padding)
        String raw = "P|5|" + NOW + "|" + DIARY_ID;
        String cursor = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/diaries/feed")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .param("sort", "popular")
                .param("cursor", cursor))
            .andExpect(status().isOk());

        ArgumentCaptor<ListPublicFeedQuery> captor = ArgumentCaptor.forClass(ListPublicFeedQuery.class);
        verify(listPublicFeedService).listPublicFeed(captor.capture());
        ListPublicFeedQuery q = captor.getValue();
        assertThat(q.sort()).isEqualTo(DiaryFeedSort.POPULAR);
        assertThat(q.recentCursor()).isEmpty();
        assertThat(q.popularCursor()).isPresent();
        PopularFeedCursor pc = q.popularCursor().orElseThrow();
        assertThat(pc.lastLikeCount()).isEqualTo(5);
        assertThat(pc.lastCreatedAt()).isEqualTo(NOW);
        assertThat(pc.lastDiaryId().value()).isEqualTo(DIARY_ID);
    }

    @Test
    void feed_accepts_lowercase_sort_value() throws Exception {
        // 누락 가능성 1 — sort 가 case-insensitive (toUpperCase 적용) 인 점 회귀 신호.
        mockValidAuth();
        when(listPublicFeedService.listPublicFeed(any()))
            .thenReturn(new FeedView(List.of(), null, false));

        mockMvc.perform(get("/api/v1/diaries/feed")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .param("sort", "recent"))
            .andExpect(status().isOk());

        ArgumentCaptor<ListPublicFeedQuery> captor = ArgumentCaptor.forClass(ListPublicFeedQuery.class);
        verify(listPublicFeedService).listPublicFeed(captor.capture());
        assertThat(captor.getValue().sort()).isEqualTo(DiaryFeedSort.RECENT);
    }

    @Test
    void feed_treats_blank_tag_param_as_no_filter() throws Exception {
        // test-reviewer L2 — tag query 가 빈 문자열 / 공백이면 Optional.empty() 로 처리되는 사양.
        mockValidAuth();
        when(listPublicFeedService.listPublicFeed(any()))
            .thenReturn(new FeedView(List.of(), null, false));

        mockMvc.perform(get("/api/v1/diaries/feed")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .param("tag", "   "))
            .andExpect(status().isOk());

        ArgumentCaptor<ListPublicFeedQuery> captor = ArgumentCaptor.forClass(ListPublicFeedQuery.class);
        verify(listPublicFeedService).listPublicFeed(captor.capture());
        assertThat(captor.getValue().tag()).isEmpty();
    }

    @Test
    void feed_returns_200_with_POPULAR_sort_and_tag_filter() throws Exception {
        mockValidAuth();
        when(listPublicFeedService.listPublicFeed(any()))
            .thenReturn(new FeedView(List.of(), null, false));

        mockMvc.perform(get("/api/v1/diaries/feed")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .param("sort", "popular")
                .param("tag", "일상")
                .param("size", "20"))
            .andExpect(status().isOk());

        ArgumentCaptor<ListPublicFeedQuery> captor = ArgumentCaptor.forClass(ListPublicFeedQuery.class);
        verify(listPublicFeedService).listPublicFeed(captor.capture());
        ListPublicFeedQuery q = captor.getValue();
        assertThat(q.sort()).isEqualTo(DiaryFeedSort.POPULAR);
        assertThat(q.size()).isEqualTo(20);
        assertThat(q.tag()).isPresent();
        assertThat(q.tag().orElseThrow().value()).isEqualTo("일상");
    }

    @Test
    void feed_returns_400_when_size_exceeds_100() throws Exception {
        mockValidAuth();

        mockMvc.perform(get("/api/v1/diaries/feed")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"));
        verify(listPublicFeedService, never()).listPublicFeed(any());
    }

    @Test
    void feed_returns_400_when_sort_unknown() throws Exception {
        mockValidAuth();

        mockMvc.perform(get("/api/v1/diaries/feed")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .param("sort", "trending"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"));
        verify(listPublicFeedService, never()).listPublicFeed(any());
    }

    @Test
    void feed_returns_400_when_cursor_invalid_base64() throws Exception {
        mockValidAuth();

        mockMvc.perform(get("/api/v1/diaries/feed")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .param("cursor", "***not-base64***"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"));
        verify(listPublicFeedService, never()).listPublicFeed(any());
    }

    @Test
    void feed_returns_400_when_tag_exceeds_30_codepoints() throws Exception {
        // Tag VO invariant — controller assemble 시점 InvalidTagException 발생
        mockValidAuth();
        String tooLong = "x".repeat(31);

        mockMvc.perform(get("/api/v1/diaries/feed")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .param("tag", tooLong))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"))
            // test-reviewer L1 — generic message 박제 (handleDomainValidation 매핑 회귀 신호)
            .andExpect(jsonPath("$.message").value("request is invalid"));
        verify(listPublicFeedService, never()).listPublicFeed(any());
    }

    @Test
    void feed_returns_401_when_no_auth_header() throws Exception {
        mockMvc.perform(get("/api/v1/diaries/feed"))
            .andExpect(status().isUnauthorized());
        verify(listPublicFeedService, never()).listPublicFeed(any());
    }

    // ============================================================
    // GET /api/v1/diaries/me (listMyFeed)
    // ============================================================

    @Test
    void me_returns_200_with_RECENT_only_default_size_10() throws Exception {
        mockValidAuth();
        when(listMyFeedService.listMyFeed(any()))
            .thenReturn(new FeedView(List.of(privateDiaryView()), null, false));

        mockMvc.perform(get("/api/v1/diaries/me")
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].visibility").value("PRIVATE"));

        ArgumentCaptor<ListMyFeedQuery> captor = ArgumentCaptor.forClass(ListMyFeedQuery.class);
        verify(listMyFeedService).listMyFeed(captor.capture());
        ListMyFeedQuery q = captor.getValue();
        assertThat(q.authorId()).isEqualTo(USER_ID);
        assertThat(q.size()).isEqualTo(10);
        assertThat(q.cursor()).isEmpty();
    }

    @Test
    void me_returns_400_when_size_below_1() throws Exception {
        mockValidAuth();

        mockMvc.perform(get("/api/v1/diaries/me")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .param("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"));
        verify(listMyFeedService, never()).listMyFeed(any());
    }

    @Test
    void me_returns_400_when_cursor_invalid() throws Exception {
        mockValidAuth();

        mockMvc.perform(get("/api/v1/diaries/me")
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .param("cursor", "***not-base64***"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"));
        verify(listMyFeedService, never()).listMyFeed(any());
    }

    @Test
    void me_returns_401_when_no_auth_header() throws Exception {
        mockMvc.perform(get("/api/v1/diaries/me"))
            .andExpect(status().isUnauthorized());
        verify(listMyFeedService, never()).listMyFeed(any());
    }

    // ============================================================
    // DELETE /api/v1/diaries/{diaryId}
    // ============================================================

    @Test
    void delete_returns_204_with_no_content() throws Exception {
        mockValidAuth();

        mockMvc.perform(delete("/api/v1/diaries/{id}", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isNoContent());

        ArgumentCaptor<DeleteDiaryCommand> captor = ArgumentCaptor.forClass(DeleteDiaryCommand.class);
        verify(deleteDiaryService).delete(captor.capture());
        DeleteDiaryCommand cmd = captor.getValue();
        assertThat(cmd.diaryId()).isEqualTo(DIARY_ID);
        assertThat(cmd.requesterId()).isEqualTo(USER_ID);
    }

    @Test
    void delete_when_already_deleted_returns_404_non_idempotent() throws Exception {
        // test-reviewer M3 — 박제 §9 비멱등 (이미 삭제 시 404). 두 번째 호출이 동일 응답이 아닌
        // 404 임을 명시 검증. 첫 호출 후 상태가 사라지므로 두 번째는 NotFoundException 으로 매핑.
        mockValidAuth();
        doThrow(new DiaryNotFoundException("not found"))
            .when(deleteDiaryService).delete(any());

        mockMvc.perform(delete("/api/v1/diaries/{id}", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("DIARY_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("diary not found"));
    }

    @Test
    void delete_returns_404_when_AccessDenied_IDOR() throws Exception {
        // test-reviewer M1 — 박제 §9 작성자 only / IDOR 통일. AccessDenied 가 NotFound 와 동일 응답으로
        // 매핑되는 사양 회귀 신호 (정보 누설 차단).
        mockValidAuth();
        doThrow(new DiaryAccessDeniedException("not owner"))
            .when(deleteDiaryService).delete(any());

        mockMvc.perform(delete("/api/v1/diaries/{id}", DIARY_ID)
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("DIARY_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("diary not found"));
    }

    @Test
    void delete_returns_400_when_path_diaryId_not_uuid() throws Exception {
        mockValidAuth();

        mockMvc.perform(delete("/api/v1/diaries/not-uuid")
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DIARY_VALIDATION_FAILED"));
        verify(deleteDiaryService, never()).delete(any());
    }

    @Test
    void delete_returns_401_when_no_auth_header() throws Exception {
        mockMvc.perform(delete("/api/v1/diaries/{id}", DIARY_ID))
            .andExpect(status().isUnauthorized());
        verify(deleteDiaryService, never()).delete(any());
    }
}
