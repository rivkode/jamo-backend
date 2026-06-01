package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerificationException;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.diary.application.dto.diarychat.MessageListView;
import app.backend.jamo.diary.application.dto.diarychat.MessageView;
import app.backend.jamo.diary.application.service.diarychat.ListMessagesService;
import app.backend.jamo.diary.application.service.diarychat.SendMessageService;
import app.backend.jamo.diary.domain.exception.ChatRoomNotFoundException;
import app.backend.jamo.diary.domain.model.diarychat.MessageSource;
import app.backend.jamo.diary.presentation.dto.diarychat.PollResponse;
import app.backend.jamo.diary.presentation.exception.DiaryChatExceptionHandler;
import app.backend.jamo.diary.presentation.web.ChatPollingCoordinator;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DiaryChatMessageController.class)
@Import({DiaryChatExceptionHandler.class, LoginUserArgumentResolver.class, PresentationWebConfig.class})
class DiaryChatMessageControllerWebMvcTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SID = "sid-1";
    private static final String DEVICE = "device-1";
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final String BEARER = "Bearer valid-access";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SendMessageService sendMessageService;
    @MockitoBean private ListMessagesService listMessagesService;
    @MockitoBean private ChatPollingCoordinator pollingCoordinator;
    @MockitoBean private JwtVerifier jwtVerifier;

    private void auth() throws JwtVerificationException {
        when(jwtVerifier.verify("valid-access")).thenReturn(new JwtClaims(
            USER.toString(), SID, DEVICE, JwtTokenType.ACCESS, NOW, NOW.plusSeconds(900)));
    }

    private MessageView msg(long id) {
        return new MessageView(id, 1L, USER, "호스트", "안녕", null, MessageSource.USER, NOW);
    }

    @Test
    void send_returns_201() throws Exception {
        auth();
        when(sendMessageService.send(any())).thenReturn(msg(42));
        mockMvc.perform(post("/api/v1/diary-chatrooms/{id}/messages", "1").header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"안녕\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.messageId").value(42))
            .andExpect(jsonPath("$.author.userId").value(USER.toString()))
            .andExpect(jsonPath("$.source").value("user"));
    }

    @Test
    void send_blank_text_400() throws Exception {
        auth();
        mockMvc.perform(post("/api/v1/diary-chatrooms/{id}/messages", "1").header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CHAT_VALIDATION_FAILED"));
    }

    @Test
    void send_without_auth_401() throws Exception {
        mockMvc.perform(post("/api/v1/diary-chatrooms/{id}/messages", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"안녕\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void send_to_inaccessible_room_404() throws Exception {
        auth();
        when(sendMessageService.send(any())).thenThrow(new ChatRoomNotFoundException("x"));
        mockMvc.perform(post("/api/v1/diary-chatrooms/{id}/messages", "1").header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"안녕\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CHAT_ROOM_NOT_FOUND"));
    }

    @Test
    void list_returns_items_hasMore() throws Exception {
        auth();
        when(listMessagesService.list(any()))
            .thenReturn(new MessageListView(List.of(msg(5), msg(4)), true, 4L));
        mockMvc.perform(get("/api/v1/diary-chatrooms/{id}/messages", "1")
                .param("size", "2").header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.hasMore").value(true))
            .andExpect(jsonPath("$.oldestMessageId").value(4));
    }

    @Test
    void poll_returns_immediate_data_via_async() throws Exception {
        auth();
        DeferredResult<PollResponse> dr = new DeferredResult<>();
        dr.setResult(PollResponse.from(
            new app.backend.jamo.diary.application.dto.diarychat.PollView(
                List.of(), List.of(), 9L)));
        when(pollingCoordinator.poll(any(), anyLong(), anyInt(), any())).thenReturn(dr);

        MvcResult mvc = mockMvc.perform(get("/api/v1/diary-chatrooms/{id}/messages/poll", "1")
                .param("after", "9").header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(mvc))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextAfter").value(9));
    }

    @Test
    void poll_inaccessible_room_404() throws Exception {
        auth();
        when(pollingCoordinator.poll(any(), anyLong(), anyInt(), any()))
            .thenThrow(new ChatRoomNotFoundException("x"));
        mockMvc.perform(get("/api/v1/diary-chatrooms/{id}/messages/poll", "1")
                .param("after", "9").header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CHAT_ROOM_NOT_FOUND"));
    }
}
