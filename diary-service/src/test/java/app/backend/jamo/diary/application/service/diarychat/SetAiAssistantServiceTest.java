package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.SetAiAssistant;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomView;
import app.backend.jamo.diary.domain.exception.ChatRoomForbiddenException;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatParticipantRepository;
import app.backend.jamo.diary.domain.repository.ChatRoomEventRepository;
import app.backend.jamo.diary.domain.repository.DiaryChatRoomRepository;
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

class SetAiAssistantServiceTest {

    private static final UUID DIARY = UUID.randomUUID();
    private static final UUID HOST = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final RoomId ROOM = RoomId.of(1);

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

    private DiaryChatRoomRepository roomRepository;
    private ChatParticipantRepository participantRepository;
    private ChatRoomEventRepository eventRepository;
    private ChatRoomAccessGuard accessGuard;
    private SetAiAssistantService service;

    @BeforeEach
    void setUp() {
        roomRepository = mock(DiaryChatRoomRepository.class);
        participantRepository = mock(ChatParticipantRepository.class);
        eventRepository = mock(ChatRoomEventRepository.class);
        accessGuard = mock(ChatRoomAccessGuard.class);
        service = new SetAiAssistantService(accessGuard, roomRepository, participantRepository, eventRepository, CLOCK);
        when(participantRepository.countByRoomId(any())).thenReturn(2L);
    }

    private DiaryChatRoom room(boolean ai) {
        return DiaryChatRoom.reconstitute(ROOM, DIARY, HOST, ai, Instant.parse("2026-06-01T10:00:00Z"), null);
    }

    @Test
    void host_toggles_and_persists() {
        DiaryChatRoom room = room(true);
        when(accessGuard.loadAccessibleRoom(ROOM, HOST)).thenReturn(room);
        when(roomRepository.save(any())).thenReturn(room);

        ChatRoomView view = service.setAiAssistant(new SetAiAssistant(ROOM, HOST, false));

        assertThat(view.aiAssistantEnabled()).isFalse();
        verify(roomRepository).save(any());
        verify(eventRepository).append(any());  // 값 변경 → ai_toggle_changed 이벤트
    }

    @Test
    void host_same_value_toggle_no_event() {
        DiaryChatRoom room = room(true);
        when(accessGuard.loadAccessibleRoom(ROOM, HOST)).thenReturn(room);
        when(roomRepository.save(any())).thenReturn(room);

        service.setAiAssistant(new SetAiAssistant(ROOM, HOST, true));  // 동일 값

        verify(eventRepository, never()).append(any());  // 변경 없음 → 이벤트 없음
    }

    @Test
    void non_host_rejected_403_without_persist() {
        when(accessGuard.loadAccessibleRoom(ROOM, OTHER)).thenReturn(room(true));

        assertThatThrownBy(() -> service.setAiAssistant(new SetAiAssistant(ROOM, OTHER, false)))
            .isInstanceOf(ChatRoomForbiddenException.class);

        verify(roomRepository, never()).save(any());
        verify(eventRepository, never()).append(any());
    }
}
