package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.Join;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JoinChatRoomServiceTest {

    private static final UUID DIARY = UUID.randomUUID();
    private static final UUID HOST = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final RoomId ROOM = RoomId.of(1);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

    private ChatParticipantRepository participantRepository;
    private ChatRoomAccessGuard accessGuard;
    private JoinChatRoomService service;

    @BeforeEach
    void setUp() {
        participantRepository = mock(ChatParticipantRepository.class);
        accessGuard = mock(ChatRoomAccessGuard.class);
        service = new JoinChatRoomService(accessGuard, participantRepository, CLOCK);
        DiaryChatRoom room = DiaryChatRoom.reconstitute(ROOM, DIARY, HOST, true, CLOCK.instant(), null);
        when(accessGuard.loadAccessibleRoom(ROOM, USER)).thenReturn(room);
        when(participantRepository.countByRoomId(ROOM)).thenReturn(1L);
    }

    @Test
    void first_join_registers_participant() {
        when(participantRepository.existsByRoomIdAndUserId(ROOM, USER)).thenReturn(false);
        service.join(new Join(ROOM, USER));
        verify(participantRepository).save(any());
    }

    @Test
    void repeat_join_is_noop_idempotent() {
        when(participantRepository.existsByRoomIdAndUserId(ROOM, USER)).thenReturn(true);
        service.join(new Join(ROOM, USER));
        verify(participantRepository, never()).save(any());
    }
}
