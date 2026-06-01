package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.SetAiAssistant;
import app.backend.jamo.diary.application.dto.diarychat.ChatRoomView;
import app.backend.jamo.diary.domain.exception.ChatRoomForbiddenException;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatParticipantRepository;
import app.backend.jamo.diary.domain.repository.DiaryChatRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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

    private DiaryChatRoomRepository roomRepository;
    private ChatParticipantRepository participantRepository;
    private ChatRoomAccessGuard accessGuard;
    private SetAiAssistantService service;

    @BeforeEach
    void setUp() {
        roomRepository = mock(DiaryChatRoomRepository.class);
        participantRepository = mock(ChatParticipantRepository.class);
        accessGuard = mock(ChatRoomAccessGuard.class);
        service = new SetAiAssistantService(accessGuard, roomRepository, participantRepository);
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
    }

    @Test
    void non_host_rejected_403_without_persist() {
        when(accessGuard.loadAccessibleRoom(ROOM, OTHER)).thenReturn(room(true));

        assertThatThrownBy(() -> service.setAiAssistant(new SetAiAssistant(ROOM, OTHER, false)))
            .isInstanceOf(ChatRoomForbiddenException.class);

        verify(roomRepository, never()).save(any());
    }
}
