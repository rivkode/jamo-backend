package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.identity.application.dto.MyProfileResult;
import app.backend.jamo.identity.application.dto.PublicProfileResult;
import app.backend.jamo.identity.application.dto.RetrieveMyProfileQuery;
import app.backend.jamo.identity.application.dto.RetrieveProfileQuery;
import app.backend.jamo.identity.application.dto.UpdateMyProfileCommand;
import app.backend.jamo.identity.application.service.RetrieveMyProfileService;
import app.backend.jamo.identity.application.service.RetrieveProfileService;
import app.backend.jamo.identity.application.service.UpdateMyProfileService;
import app.backend.jamo.identity.domain.exception.AuthenticatedUserNotFoundException;
import app.backend.jamo.identity.domain.exception.DisplayNameChangeTooFrequentException;
import app.backend.jamo.identity.domain.exception.UserNotFoundException;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.profile.AvatarUrl;
import app.backend.jamo.identity.domain.model.profile.Bio;
import app.backend.jamo.identity.domain.model.profile.Locale;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.presentation.exception.AuthExceptionHandler;
import app.backend.jamo.identity.presentation.exception.ProfileExceptionHandler;
import app.backend.jamo.identity.presentation.web.LoginUserArgumentResolver;
import app.backend.jamo.identity.presentation.web.PresentationWebConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProfileController.class)
@Import({ProfileExceptionHandler.class, AuthExceptionHandler.class,
        LoginUserArgumentResolver.class, PresentationWebConfig.class})
class ProfileControllerTest {

    private static final UserId USER_ID = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final String SID = "sid-1";
    private static final String DEVICE_ID = "device-1";
    private static final Instant NOW = Instant.parse("2026-04-28T10:00:00Z");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RetrieveMyProfileService retrieveMyProfileService;
    @MockitoBean private RetrieveProfileService retrieveProfileService;
    @MockitoBean private UpdateMyProfileService updateMyProfileService;
    @MockitoBean private JwtVerifier jwtVerifier;

    private JwtClaims accessClaims() {
        return new JwtClaims(USER_ID.asString(), SID, DEVICE_ID,
                JwtTokenType.ACCESS, NOW, NOW.plusSeconds(900));
    }

    private MyProfileResult fullResult() {
        return new MyProfileResult(
                USER_ID,
                new Email("u@jamoai.app"),
                new DisplayName("jamo"),
                List.of(OAuthProvider.GOOGLE),
                NOW,
                new Bio("hello"),
                new AvatarUrl("https://e.io/a.png"),
                new Locale("en"));
    }

    // ------------------------------------------------------------- GET /me

    @Test
    void getMyProfile_returns_200_with_8_fields() throws Exception {
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());
        when(retrieveMyProfileService.retrieve(any())).thenReturn(fullResult());

        mockMvc.perform(get("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.value().toString()))
                .andExpect(jsonPath("$.email").value("u@jamoai.app"))
                .andExpect(jsonPath("$.displayName").value("jamo"))
                .andExpect(jsonPath("$.providers[0]").value("GOOGLE"))
                .andExpect(jsonPath("$.createdAt").value("2026-04-28T10:00:00Z"))
                .andExpect(jsonPath("$.bio").value("hello"))
                .andExpect(jsonPath("$.avatarUrl").value("https://e.io/a.png"))
                .andExpect(jsonPath("$.locale").value("en"));
    }

    @Test
    void getMyProfile_returns_401_UNAUTHORIZED_with_AuthErrorResponse_when_no_auth_header() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));  // test review H1: body code 검증
        verify(retrieveMyProfileService, never()).retrieve(any());
    }

    @Test
    void getMyProfile_uses_authenticated_userId_in_query() throws Exception {
        // test review M2: GET 의 ArgumentCaptor — auth.userId() 가 정확히 query 에 매핑되어야 IDOR 회귀 차단
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());
        when(retrieveMyProfileService.retrieve(any())).thenReturn(fullResult());

        ArgumentCaptor<RetrieveMyProfileQuery> captor = ArgumentCaptor.forClass(RetrieveMyProfileQuery.class);
        mockMvc.perform(get("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access"))
                .andExpect(status().isOk());

        verify(retrieveMyProfileService).retrieve(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
    }

    @Test
    void getMyProfile_returns_500_INTERNAL_ERROR_when_authenticated_user_missing() throws Exception {
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());
        when(retrieveMyProfileService.retrieve(any()))
                .thenThrow(new AuthenticatedUserNotFoundException("missing"));

        MvcResult result = mockMvc.perform(get("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("missing");  // sanitize 검증
    }

    // ------------------------------------------------------------ GET /{userId}

    @Test
    void getProfile_returns_200_with_4_fields_only() throws Exception {
        UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());
        when(retrieveProfileService.retrieve(any())).thenReturn(new PublicProfileResult(
                new UserId(targetId),
                new DisplayName("nick"),
                new Bio("hi"),
                new AvatarUrl("https://e.io/x.png")));

        mockMvc.perform(get("/api/v1/profiles/" + targetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetId.toString()))
                .andExpect(jsonPath("$.displayName").value("nick"))
                .andExpect(jsonPath("$.bio").value("hi"))
                .andExpect(jsonPath("$.avatarUrl").value("https://e.io/x.png"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.providers").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.locale").doesNotExist());
    }

    @Test
    void getProfile_returns_404_USER_NOT_FOUND_when_target_missing() throws Exception {
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());
        when(retrieveProfileService.retrieve(any()))
                .thenThrow(new UserNotFoundException("not found"));

        mockMvc.perform(get("/api/v1/profiles/00000000-0000-0000-0000-000000000099")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void getProfile_returns_400_VALIDATION_FAILED_when_userId_not_uuid() throws Exception {
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());

        mockMvc.perform(get("/api/v1/profiles/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(retrieveProfileService, never()).retrieve(any());
    }

    @Test
    void getProfile_passes_path_userId_to_query_unchanged() throws Exception {
        // test review M2: path 변수 그대로 query 에 전달되어야 (IDOR 회귀 신호)
        UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());
        when(retrieveProfileService.retrieve(any())).thenReturn(new PublicProfileResult(
                new UserId(targetId), new DisplayName("nick"), null, null));

        ArgumentCaptor<RetrieveProfileQuery> captor = ArgumentCaptor.forClass(RetrieveProfileQuery.class);
        mockMvc.perform(get("/api/v1/profiles/" + targetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access"))
                .andExpect(status().isOk());

        verify(retrieveProfileService).retrieve(captor.capture());
        assertThat(captor.getValue().userId().value()).isEqualTo(targetId);
    }

    // ------------------------------------------------------- PATCH /me

    @Test
    void updateMyProfile_returns_200_with_full_fields_after_change() throws Exception {
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());
        when(updateMyProfileService.update(any(UpdateMyProfileCommand.class))).thenReturn(fullResult());

        String body = "{\"displayName\":\"jamo\",\"bio\":\"hello\",\"avatarUrl\":\"https://e.io/a.png\",\"locale\":\"en\"}";

        ArgumentCaptor<UpdateMyProfileCommand> captor = ArgumentCaptor.forClass(UpdateMyProfileCommand.class);
        mockMvc.perform(patch("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("jamo"))
                .andExpect(jsonPath("$.bio").value("hello"));

        verify(updateMyProfileService).update(captor.capture());
        UpdateMyProfileCommand cmd = captor.getValue();
        assertThat(cmd.userId()).isEqualTo(USER_ID);
        assertThat(cmd.displayName()).isEqualTo("jamo");
        assertThat(cmd.bio()).isEqualTo("hello");
        assertThat(cmd.avatarUrl()).isEqualTo("https://e.io/a.png");
        assertThat(cmd.locale()).isEqualTo("en");
    }

    @Test
    void updateMyProfile_returns_400_DISPLAY_NAME_CHANGE_TOO_FREQUENT_when_rate_limit_hit() throws Exception {
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());
        when(updateMyProfileService.update(any()))
                .thenThrow(new DisplayNameChangeTooFrequentException("rate"));

        mockMvc.perform(patch("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"new\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DISPLAY_NAME_CHANGE_TOO_FREQUENT"));
    }

    @Test
    void updateMyProfile_returns_400_VALIDATION_FAILED_when_displayName_too_long() throws Exception {
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());
        String tooLong = "x".repeat(33);
        String body = "{\"displayName\":\"" + tooLong + "\"}";

        mockMvc.perform(patch("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(updateMyProfileService, never()).update(any());
    }

    @Test
    void updateMyProfile_returns_400_VALIDATION_FAILED_when_avatarUrl_userInfo() throws Exception {
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());
        when(updateMyProfileService.update(any()))
                .thenThrow(new IllegalArgumentException("avatarUrl must not contain userInfo"));

        MvcResult result = mockMvc.perform(patch("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":\"https://user:pass@e.io/a.png\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn();

        // sanitize: 도메인 raw message 미노출
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("userInfo");
    }

    @Test
    void updateMyProfile_ignores_unknown_fields_like_email() throws Exception {
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());
        when(updateMyProfileService.update(any())).thenReturn(fullResult());

        // email / providers / id 는 화이트리스트 외 — Jackson ignoreUnknown 으로 무시
        String body = "{\"displayName\":\"jamo\",\"email\":\"hacker@e.io\",\"providers\":[\"FAKE\"]}";

        ArgumentCaptor<UpdateMyProfileCommand> captor = ArgumentCaptor.forClass(UpdateMyProfileCommand.class);
        mockMvc.perform(patch("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(updateMyProfileService).update(captor.capture());
        // command 에 화이트리스트 4 필드 외 정보 침투 없음
        assertThat(captor.getValue().displayName()).isEqualTo("jamo");
    }

    @Test
    void updateMyProfile_returns_401_UNAUTHORIZED_with_AuthErrorResponse_when_no_auth_header() throws Exception {
        mockMvc.perform(patch("/api/v1/profiles/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"jamo\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));  // test review H1
        verify(updateMyProfileService, never()).update(any());
    }

    @Test
    void updateMyProfile_returns_200_with_all_null_command_when_empty_body() throws Exception {
        // test review M3: PATCH null = no-op 사양 — 빈 body `{}` 가 Service 까지 도달, Command 4 필드 모두 null
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());
        when(updateMyProfileService.update(any())).thenReturn(fullResult());

        ArgumentCaptor<UpdateMyProfileCommand> captor = ArgumentCaptor.forClass(UpdateMyProfileCommand.class);
        mockMvc.perform(patch("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(updateMyProfileService).update(captor.capture());
        UpdateMyProfileCommand cmd = captor.getValue();
        assertThat(cmd.userId()).isEqualTo(USER_ID);
        assertThat(cmd.displayName()).isNull();
        assertThat(cmd.bio()).isNull();
        assertThat(cmd.avatarUrl()).isNull();
        assertThat(cmd.locale()).isNull();
    }

    @Test
    void updateMyProfile_returns_400_VALIDATION_FAILED_when_displayName_blank() throws Exception {
        // test review M4: @Size(min=1) 빈 문자열 차단 — Bean Validation 단계에서 거부
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());

        mockMvc.perform(patch("/api/v1/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(updateMyProfileService, never()).update(any());
    }
}
