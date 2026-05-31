package app.backend.jamo.identity.presentation.controller;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.identity.application.dto.AuthExchangeResult;
import app.backend.jamo.identity.application.dto.AuthLoginCommand;
import app.backend.jamo.identity.application.dto.MyProfileResult;
import app.backend.jamo.identity.application.dto.PublicProfileResult;
import app.backend.jamo.identity.application.service.AuthLoginService;
import app.backend.jamo.identity.application.service.RetrieveMyProfileService;
import app.backend.jamo.identity.application.service.RetrieveProfileService;
import app.backend.jamo.identity.application.service.UpdateMyProfileService;
import app.backend.jamo.identity.domain.model.oauth.OAuthProvider;
import app.backend.jamo.identity.domain.model.profile.AvatarUrl;
import app.backend.jamo.identity.domain.model.profile.Bio;
import app.backend.jamo.identity.domain.model.profile.Locale;
import app.backend.jamo.identity.domain.model.user.DisplayName;
import app.backend.jamo.identity.domain.model.user.Email;
import app.backend.jamo.identity.domain.model.user.UserId;
import app.backend.jamo.identity.presentation.exception.AuthExceptionHandler;
import app.backend.jamo.identity.presentation.exception.ProfileExceptionHandler;
import app.backend.jamo.identity.presentation.web.DeviceIdResolver;
import app.backend.jamo.identity.presentation.web.DeviceIdResult;
import app.backend.jamo.identity.presentation.web.LoginUserArgumentResolver;
import app.backend.jamo.identity.presentation.web.PresentationWebConfig;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 0526_flutter.md §1 path alias 검증 (Slice 2).
 *
 * <p>{@link UserPathAliasController} 가 {@link AuthLoginController} / {@link ProfileController} 와
 * 동일하게 동작하는지 확인. WebMvcTest 슬라이스로 alias path 3개를 검증.
 */
@WebMvcTest(controllers = {
    UserPathAliasController.class,
    AuthLoginController.class,
    ProfileController.class
})
@Import({AuthExceptionHandler.class, ProfileExceptionHandler.class,
    LoginUserArgumentResolver.class, PresentationWebConfig.class})
class UserPathAliasControllerTest {

    private static final UserId USER_ID = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final String SID = "sid-1";
    private static final String DEVICE_ID = "device-1";
    private static final Instant NOW = Instant.parse("2026-05-27T10:00:00Z");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthLoginService authLoginService;
    @MockitoBean private RetrieveMyProfileService retrieveMyProfileService;
    @MockitoBean private RetrieveProfileService retrieveProfileService;
    @MockitoBean private UpdateMyProfileService updateMyProfileService;
    @MockitoBean private DeviceIdResolver deviceIdResolver;
    @MockitoBean private JwtVerifier jwtVerifier;

    private JwtClaims accessClaims() {
        return new JwtClaims(USER_ID.asString(), SID, DEVICE_ID,
            JwtTokenType.ACCESS, NOW, NOW.plusSeconds(900));
    }

    private MyProfileResult fullProfile() {
        return new MyProfileResult(
            USER_ID,
            new Email("u@jamoai.app"),
            new DisplayName("Minji"),
            List.of(OAuthProvider.GOOGLE),
            NOW,
            new Bio("hello"),
            new AvatarUrl("https://e.io/a.png"),
            new Locale("ko"),
            7L);
    }

    // ---------------- /api/v1/users/login (PRD §1.1)

    @Test
    void users_login_alias_returns_200_with_jwt_pair_and_exposes_expiresIn_tokenType() throws Exception {
        when(deviceIdResolver.resolve(any())).thenReturn(new DeviceIdResult("device-1234", false));
        when(authLoginService.login(any(AuthLoginCommand.class)))
            .thenReturn(new AuthExchangeResult(USER_ID, "access-jwt", "refresh-jwt", 900L));

        mockMvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"u@jamoai.app\",\"password\":\"Passw0rd!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(USER_ID.asString()))
            .andExpect(jsonPath("$.accessToken").value("access-jwt"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-jwt"))
            .andExpect(jsonPath("$.expiresInSeconds").value(900))
            // PRD §0.2 alias 동시 노출
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(authLoginService).login(any(AuthLoginCommand.class));
    }

    @Test
    void users_login_alias_rejects_invalid_email() throws Exception {
        mockMvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-email\",\"password\":\"Passw0rd!\"}"))
            .andExpect(status().isBadRequest());
        verify(authLoginService, never()).login(any());
    }

    // ---------------- /api/v1/users/me (PRD §1.5)

    @Test
    void users_me_alias_returns_200_with_username_and_provider_aliases() throws Exception {
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());
        when(retrieveMyProfileService.retrieve(any())).thenReturn(fullProfile());

        mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(USER_ID.value().toString()))
            .andExpect(jsonPath("$.email").value("u@jamoai.app"))
            .andExpect(jsonPath("$.displayName").value("Minji"))
            .andExpect(jsonPath("$.username").value("Minji"))         // PRD §1.5 alias
            .andExpect(jsonPath("$.providers[0]").value("GOOGLE"))
            .andExpect(jsonPath("$.provider").value("GOOGLE"));        // PRD §1.5 단수 alias
    }

    @Test
    void users_me_alias_returns_401_when_no_auth_header() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(retrieveMyProfileService, never()).retrieve(any());
    }

    // ---------------- /api/v1/users/{userId} (PRD §1.6)

    @Test
    void users_userId_alias_returns_200_with_public_4_fields_plus_username_alias() throws Exception {
        UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());
        when(retrieveProfileService.retrieve(any())).thenReturn(new PublicProfileResult(
            new UserId(targetId),
            new DisplayName("Nick"),
            new Bio("hi"),
            new AvatarUrl("https://e.io/x.png"),
            3L));

        mockMvc.perform(get("/api/v1/users/" + targetId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(targetId.toString()))
            .andExpect(jsonPath("$.displayName").value("Nick"))
            .andExpect(jsonPath("$.username").value("Nick"))   // PRD §1.6 alias
            .andExpect(jsonPath("$.bio").value("hi"))
            .andExpect(jsonPath("$.avatarUrl").value("https://e.io/x.png"))
            // private 필드는 노출되지 않음 — 4 필드만
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.providers").doesNotExist());
    }

    @Test
    void users_userId_alias_returns_400_when_userId_not_uuid() throws Exception {
        when(jwtVerifier.verify("valid-access")).thenReturn(accessClaims());

        mockMvc.perform(get("/api/v1/users/not-uuid")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-access"))
            .andExpect(status().isBadRequest());
        verify(retrieveProfileService, never()).retrieve(any());
    }

    // ---------------- 위임 등가성 (test-reviewer M3)

    @Test
    void users_login_alias_invokes_setDeviceCookie_same_as_auth_login() throws Exception {
        // alias path 가 원본의 부수효과 (DeviceIdResolver.setDeviceCookie) 까지 정확히 위임하는지 검증.
        // isNewlyGenerated=true 시 setDeviceCookie 호출되어야 한다 — 원본 controller 와 동일 흐름.
        when(deviceIdResolver.resolve(any())).thenReturn(new DeviceIdResult("device-new", true));
        when(authLoginService.login(any(AuthLoginCommand.class)))
            .thenReturn(new AuthExchangeResult(USER_ID, "access-jwt", "refresh-jwt", 900L));

        mockMvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"u@jamoai.app\",\"password\":\"Passw0rd!\"}"))
            .andExpect(status().isOk());

        verify(deviceIdResolver).setDeviceCookie(any(), eq("device-new"));
    }

    @Test
    void users_login_and_auth_login_produce_equivalent_response() throws Exception {
        // 핵심 등가성 — 같은 입력으로 양쪽 path 응답 body 가 동일해야 한다 (위임 무결성 회귀 신호).
        when(deviceIdResolver.resolve(any())).thenReturn(new DeviceIdResult("device-1", false));
        when(authLoginService.login(any(AuthLoginCommand.class)))
            .thenReturn(new AuthExchangeResult(USER_ID, "access-jwt", "refresh-jwt", 900L));

        String aliasBody = mockMvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"u@jamoai.app\",\"password\":\"Passw0rd!\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String originBody = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"u@jamoai.app\",\"password\":\"Passw0rd!\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(aliasBody).isEqualTo(originBody);
    }
}
