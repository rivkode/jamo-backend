package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.diary.application.dto.diarychat.MessageCommands.ListMessages;
import app.backend.jamo.diary.application.dto.diarychat.MessageCommands.Send;
import app.backend.jamo.diary.application.dto.diarychat.MessageListView;
import app.backend.jamo.diary.application.dto.diarychat.MessageView;
import app.backend.jamo.diary.application.service.diarychat.ListMessagesService;
import app.backend.jamo.diary.application.service.diarychat.SendMessageService;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.presentation.dto.diarychat.MessageListResponse;
import app.backend.jamo.diary.presentation.dto.diarychat.MessageResponse;
import app.backend.jamo.diary.presentation.dto.diarychat.PollResponse;
import app.backend.jamo.diary.presentation.dto.diarychat.SendMessageRequest;
import app.backend.jamo.diary.presentation.web.AuthenticatedUser;
import app.backend.jamo.diary.presentation.web.ChatPollingCoordinator;
import app.backend.jamo.diary.presentation.web.LoginUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

/**
 * Diary Chatroom 메시지/롱폴 HTTP API — S2-b (API_SPEC 부록 E.2 E2.7~E2.9). 모두 🔒 인증.
 *
 * <ul>
 *   <li>POST /{roomId}/messages — 사용자 메시지 작성 (201)</li>
 *   <li>GET /{roomId}/messages?before&size — 과거 페이지 (200, 최근 desc)</li>
 *   <li>GET /{roomId}/messages/poll?after&wait — 롱폴 (200, DeferredResult — servlet thread 비점유)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/diary-chatrooms")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerJwt")
public class DiaryChatMessageController {

    /** poll wait 상한 — 게이트웨이 idle timeout 보다 짧게 (v2 §8-b). */
    static final int MAX_WAIT_SECONDS = 30;
    static final int DEFAULT_WAIT_SECONDS = 25;

    private final SendMessageService sendMessageService;
    private final ListMessagesService listMessagesService;
    private final ChatPollingCoordinator pollingCoordinator;

    @PostMapping("/{roomId}/messages")
    public ResponseEntity<MessageResponse> send(
        @LoginUser AuthenticatedUser auth,
        @PathVariable String roomId,
        @Valid @RequestBody SendMessageRequest body
    ) {
        MessageView view = sendMessageService.send(
            new Send(RoomId.fromString(roomId), auth.userId(), body.text(), body.audioUrl()));
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.from(view));
    }

    @GetMapping("/{roomId}/messages")
    public MessageListResponse list(
        @LoginUser AuthenticatedUser auth,
        @PathVariable String roomId,
        @RequestParam(value = "before", required = false) Long before,
        @RequestParam(value = "size", required = false, defaultValue = "0") int size
    ) {
        MessageListView view = listMessagesService.list(
            new ListMessages(RoomId.fromString(roomId), auth.userId(), before, size));
        return MessageListResponse.from(view);
    }

    @GetMapping("/{roomId}/messages/poll")
    public DeferredResult<PollResponse> poll(
        @LoginUser AuthenticatedUser auth,
        @PathVariable String roomId,
        @RequestParam(value = "after", required = false, defaultValue = "0") long after,
        @RequestParam(value = "wait", required = false, defaultValue = "0") int wait
    ) {
        int waitSeconds = clampWait(wait);
        return pollingCoordinator.poll(RoomId.fromString(roomId), after, waitSeconds, auth.userId());
    }

    private int clampWait(int requested) {
        if (requested <= 0) {
            return DEFAULT_WAIT_SECONDS;
        }
        return Math.min(requested, MAX_WAIT_SECONDS);
    }
}
