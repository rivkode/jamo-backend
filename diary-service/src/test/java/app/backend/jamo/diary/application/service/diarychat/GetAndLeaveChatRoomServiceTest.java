package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.Get;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.Leave;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomView;
import app.backend.jamo.diary.domain.exception.ChatRoomNotFoundException;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatParticipantRepository;
import app.backend.jamo.diary.domain.repository.ChatRoomEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GetChatRoomService / LeaveChatRoomService 단위 검증 — 가드 위임 + 가드 404 전파 시 부작용 차단.
 */
class GetAndLeaveChatRoomServiceTest {

    private static final UUID DIARY = UUID.randomUUID();
    private static final UUID HOST = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final RoomId ROOM = RoomId.of(1);

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

    private ChatParticipantRepository participantRepository;
    private ChatRoomEventRepository eventRepository;
    private ChatRoomAccessGuard accessGuard;
    private GetChatRoomService getService;
    private LeaveChatRoomService leaveService;

    @BeforeEach
    void setUp() {
        participantRepository = mock(ChatParticipantRepository.class);
        eventRepository = mock(ChatRoomEventRepository.class);
        accessGuard = mock(ChatRoomAccessGuard.class);
        getService = new GetChatRoomService(accessGuard, participantRepository);
        leaveService = new LeaveChatRoomService(accessGuard, participantRepository, eventRepository, CLOCK);
    }

    private DiaryChatRoom room() {
        return DiaryChatRoom.reconstitute(ROOM, DIARY, HOST, true, Instant.parse("2026-06-01T10:00:00Z"), null);
    }

    @Test
    void get_returns_view_with_participant_count() {
        when(accessGuard.loadAccessibleRoom(ROOM, USER)).thenReturn(room());
        when(participantRepository.countByRoomId(ROOM)).thenReturn(3L);

        ChatRoomView view = getService.get(new Get(ROOM, USER));

        assertThat(view.roomId()).isEqualTo(1);
        assertThat(view.participantCount()).isEqualTo(3);
    }

    @Test
    void get_propagates_guard_404() {
        when(accessGuard.loadAccessibleRoom(ROOM, USER)).thenThrow(new ChatRoomNotFoundException("x"));
        assertThatThrownBy(() -> getService.get(new Get(ROOM, USER)))
            .isInstanceOf(ChatRoomNotFoundException.class);
    }

    @Test
    void leave_actually_removed_appends_event() {
        when(accessGuard.loadAccessibleRoom(ROOM, USER)).thenReturn(room());
        when(participantRepository.deleteByRoomIdAndUserId(ROOM, USER)).thenReturn(1);
        leaveService.leave(new Leave(ROOM, USER));
        verify(participantRepository).deleteByRoomIdAndUserId(ROOM, USER);
        verify(eventRepository).append(any());  // participant_left
    }

    @Test
    void leave_non_participant_no_event() {
        when(accessGuard.loadAccessibleRoom(ROOM, USER)).thenReturn(room());
        when(participantRepository.deleteByRoomIdAndUserId(ROOM, USER)).thenReturn(0);  // 없던 참여
        leaveService.leave(new Leave(ROOM, USER));
        verify(eventRepository, never()).append(any());
    }

    @Test
    void leave_guard_404_does_not_delete() {
        when(accessGuard.loadAccessibleRoom(ROOM, USER)).thenThrow(new ChatRoomNotFoundException("x"));
        assertThatThrownBy(() -> leaveService.leave(new Leave(ROOM, USER)))
            .isInstanceOf(ChatRoomNotFoundException.class);
        verify(participantRepository, never()).deleteByRoomIdAndUserId(ROOM, USER);
    }
}
