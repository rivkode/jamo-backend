package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.CreateOrGet;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.Get;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.Join;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.Leave;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.ListParticipants;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.SetAiAssistant;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomResult;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomView;
import app.backend.jamo.diary.application.dto.diarychat.ParticipantView;
import app.backend.jamo.diary.application.service.diarychat.CreateOrGetChatRoomService;
import app.backend.jamo.diary.application.service.diarychat.GetChatRoomService;
import app.backend.jamo.diary.application.service.diarychat.JoinChatRoomService;
import app.backend.jamo.diary.application.service.diarychat.LeaveChatRoomService;
import app.backend.jamo.diary.application.service.diarychat.ListParticipantsService;
import app.backend.jamo.diary.application.service.diarychat.SetAiAssistantService;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.presentation.dto.diarychat.ChatRoomResponse;
import app.backend.jamo.diary.presentation.dto.diarychat.CreateChatRoomRequest;
import app.backend.jamo.diary.presentation.dto.diarychat.ParticipantListResponse;
import app.backend.jamo.diary.presentation.dto.diarychat.ToggleAiAssistantRequest;
import app.backend.jamo.diary.presentation.web.AuthenticatedUser;
import app.backend.jamo.diary.presentation.web.LoginUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Diary Chatroom HTTP API — S2-a (API_SPEC 부록 E.2, 6 endpoint). 모두 🔒 인증.
 *
 * <ul>
 *   <li>POST /api/v1/diary-chatrooms — createOrGet (신규 201 / 기존 200)</li>
 *   <li>GET /{roomId} — 단건 조회 (200)</li>
 *   <li>GET /{roomId}/participants — 참여자 목록 (200)</li>
 *   <li>POST /{roomId}/join — 참여 (200, 멱등)</li>
 *   <li>POST /{roomId}/leave — 해제 (204, 멱등)</li>
 *   <li>POST /{roomId}/ai-toggle — host 만 (200, 비호스트 403)</li>
 * </ul>
 *
 * <p>roomId path 는 문자열 → {@link RoomId#fromString} 파싱 (형식 오류 IAE → 400). 메시지/롱폴은 S2-b.
 */
@RestController
@RequestMapping("/api/v1/diary-chatrooms")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerJwt")
public class DiaryChatRoomController {

    private final CreateOrGetChatRoomService createOrGetService;
    private final GetChatRoomService getService;
    private final JoinChatRoomService joinService;
    private final LeaveChatRoomService leaveService;
    private final SetAiAssistantService setAiAssistantService;
    private final ListParticipantsService listParticipantsService;

    @PostMapping
    public ResponseEntity<ChatRoomResponse> createOrGet(
        @LoginUser AuthenticatedUser auth,
        @Valid @RequestBody CreateChatRoomRequest body
    ) {
        UUID diaryId = parseUuid(body.diaryId());
        boolean aiEnabled = body.aiAssistantEnabled() == null || body.aiAssistantEnabled();
        ChatRoomResult result = createOrGetService.createOrGet(
            new CreateOrGet(diaryId, auth.userId(), aiEnabled));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ChatRoomResponse.from(result.view()));
    }

    @GetMapping("/{roomId}")
    public ChatRoomResponse get(@LoginUser AuthenticatedUser auth, @PathVariable String roomId) {
        ChatRoomView view = getService.get(new Get(RoomId.fromString(roomId), auth.userId()));
        return ChatRoomResponse.from(view);
    }

    @GetMapping("/{roomId}/participants")
    public ParticipantListResponse participants(
        @LoginUser AuthenticatedUser auth, @PathVariable String roomId
    ) {
        List<ParticipantView> views = listParticipantsService.list(
            new ListParticipants(RoomId.fromString(roomId), auth.userId()));
        return ParticipantListResponse.from(views);
    }

    @PostMapping("/{roomId}/join")
    public ChatRoomResponse join(@LoginUser AuthenticatedUser auth, @PathVariable String roomId) {
        ChatRoomView view = joinService.join(new Join(RoomId.fromString(roomId), auth.userId()));
        return ChatRoomResponse.from(view);
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Void> leave(@LoginUser AuthenticatedUser auth, @PathVariable String roomId) {
        leaveService.leave(new Leave(RoomId.fromString(roomId), auth.userId()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{roomId}/ai-toggle")
    public ChatRoomResponse aiToggle(
        @LoginUser AuthenticatedUser auth,
        @PathVariable String roomId,
        @Valid @RequestBody ToggleAiAssistantRequest body
    ) {
        ChatRoomView view = setAiAssistantService.setAiAssistant(
            new SetAiAssistant(RoomId.fromString(roomId), auth.userId(), body.enabled()));
        return ChatRoomResponse.from(view);
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid diaryId: " + value, e);
        }
    }
}
