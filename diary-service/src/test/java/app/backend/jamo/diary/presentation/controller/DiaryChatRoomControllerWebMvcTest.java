package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerificationException;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomResult;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomView;
import app.backend.jamo.diary.application.dto.diarychat.ParticipantView;
import app.backend.jamo.diary.application.service.diarychat.CreateOrGetChatRoomService;
import app.backend.jamo.diary.application.service.diarychat.GetChatRoomService;
import app.backend.jamo.diary.application.service.diarychat.JoinChatRoomService;
import app.backend.jamo.diary.application.service.diarychat.LeaveChatRoomService;
import app.backend.jamo.diary.application.service.diarychat.ListParticipantsService;
import app.backend.jamo.diary.application.service.diarychat.SetAiAssistantService;
import app.backend.jamo.diary.domain.exception.ChatRoomForbiddenException;
import app.backend.jamo.diary.domain.exception.ChatRoomNotFoundException;
import app.backend.jamo.diary.presentation.exception.DiaryChatExceptionHandler;
import app.backend.jamo.diary.presentation.web.LoginUserArgumentResolver;
import app.backend.jamo.diary.presentation.web.PresentationWebConfig;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DiaryChatRoomController.class)
@Import({DiaryChatExceptionHandler.class, LoginUserArgumentResolver.class, PresentationWebConfig.class})
class DiaryChatRoomControllerWebMvcTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DIARY = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String SID = "sid-1";
    private static final String DEVICE = "device-1";
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final String BEARER = "Bearer valid-access";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CreateOrGetChatRoomService createOrGetService;
    @MockitoBean private GetChatRoomService getService;
    @MockitoBean private JoinChatRoomService joinService;
    @MockitoBean private LeaveChatRoomService leaveService;
    @MockitoBean private SetAiAssistantService setAiAssistantService;
    @MockitoBean private ListParticipantsService listParticipantsService;
    @MockitoBean private JwtVerifier jwtVerifier;

    private void auth() throws JwtVerificationException {
        when(jwtVerifier.verify("valid-access")).thenReturn(new JwtClaims(
            USER.toString(), SID, DEVICE, JwtTokenType.ACCESS, NOW, NOW.plusSeconds(900)));
    }

    private ChatRoomView view(boolean ai) {
        return new ChatRoomView(1L, DIARY, USER, ai, 1L, NOW);
    }

    @Test
    void createOrGet_new_returns_201() throws Exception {
        auth();
        when(createOrGetService.createOrGet(any())).thenReturn(new ChatRoomResult(view(true), true));
        mockMvc.perform(post("/api/v1/diary-chatrooms").header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"diaryId\":\"" + DIARY + "\",\"aiAssistantEnabled\":true}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.roomId").value(1))
            .andExpect(jsonPath("$.diaryId").value(DIARY.toString()))
            .andExpect(jsonPath("$.aiAssistantEnabled").value(true));
    }

    @Test
    void createOrGet_existing_returns_200() throws Exception {
        auth();
        when(createOrGetService.createOrGet(any())).thenReturn(new ChatRoomResult(view(true), false));
        mockMvc.perform(post("/api/v1/diary-chatrooms").header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"diaryId\":\"" + DIARY + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void createOrGet_without_auth_401() throws Exception {
        mockMvc.perform(post("/api/v1/diary-chatrooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"diaryId\":\"" + DIARY + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void createOrGet_invalid_diaryId_400() throws Exception {
        auth();
        mockMvc.perform(post("/api/v1/diary-chatrooms").header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"diaryId\":\"not-a-uuid\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CHAT_VALIDATION_FAILED"));
    }

    @Test
    void get_returns_200() throws Exception {
        auth();
        when(getService.get(any())).thenReturn(view(false));
        mockMvc.perform(get("/api/v1/diary-chatrooms/{id}", "1").header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roomId").value(1));
    }

    @Test
    void get_not_found_404() throws Exception {
        auth();
        when(getService.get(any())).thenThrow(new ChatRoomNotFoundException("x"));
        mockMvc.perform(get("/api/v1/diary-chatrooms/{id}", "999").header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CHAT_ROOM_NOT_FOUND"));
    }

    @Test
    void get_non_numeric_roomId_400() throws Exception {
        auth();
        mockMvc.perform(get("/api/v1/diary-chatrooms/{id}", "abc").header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CHAT_VALIDATION_FAILED"));
    }

    @Test
    void participants_returns_items() throws Exception {
        auth();
        when(listParticipantsService.list(any())).thenReturn(List.of(
            new ParticipantView(USER, "호스트", true, NOW)));
        mockMvc.perform(get("/api/v1/diary-chatrooms/{id}/participants", "1").header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].user.userId").value(USER.toString()))
            .andExpect(jsonPath("$.items[0].user.username").value("호스트"))
            .andExpect(jsonPath("$.items[0].isHost").value(true));
    }

    @Test
    void join_returns_200() throws Exception {
        auth();
        when(joinService.join(any())).thenReturn(view(true));
        mockMvc.perform(post("/api/v1/diary-chatrooms/{id}/join", "1").header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.participantCount").value(1));
    }

    @Test
    void leave_returns_204() throws Exception {
        auth();
        mockMvc.perform(post("/api/v1/diary-chatrooms/{id}/leave", "1").header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isNoContent());
    }

    @Test
    void aiToggle_host_returns_200() throws Exception {
        auth();
        when(setAiAssistantService.setAiAssistant(any())).thenReturn(view(false));
        mockMvc.perform(post("/api/v1/diary-chatrooms/{id}/ai-toggle", "1").header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aiAssistantEnabled").value(false));
    }

    @Test
    void aiToggle_non_host_returns_403() throws Exception {
        auth();
        when(setAiAssistantService.setAiAssistant(any())).thenThrow(new ChatRoomForbiddenException("not host"));
        mockMvc.perform(post("/api/v1/diary-chatrooms/{id}/ai-toggle", "1").header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CHAT_ROOM_FORBIDDEN"));
    }

    @Test
    void aiToggle_missing_enabled_400() throws Exception {
        auth();
        mockMvc.perform(post("/api/v1/diary-chatrooms/{id}/ai-toggle", "1").header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }
}
