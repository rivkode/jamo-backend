package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.ChatRoomCommands.ListParticipants;
import app.backend.jamo.diary.application.dto.diarychat.ParticipantView;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.model.diarychat.ChatParticipant;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListParticipantsServiceTest {

    private static final UUID DIARY = UUID.randomUUID();
    private static final UUID HOST = UUID.randomUUID();
    private static final UUID GUEST = UUID.randomUUID();
    private static final RoomId ROOM = RoomId.of(1);

    private ChatParticipantRepository participantRepository;
    private ChatRoomAccessGuard accessGuard;
    private UserSummaryPort userSummaryPort;
    private ListParticipantsService service;

    @BeforeEach
    void setUp() {
        participantRepository = mock(ChatParticipantRepository.class);
        accessGuard = mock(ChatRoomAccessGuard.class);
        userSummaryPort = mock(UserSummaryPort.class);
        service = new ListParticipantsService(accessGuard, participantRepository, userSummaryPort);
        DiaryChatRoom room = DiaryChatRoom.reconstitute(ROOM, DIARY, HOST, true, Instant.parse("2026-06-01T10:00:00Z"), null);
        when(accessGuard.loadAccessibleRoom(ROOM, HOST)).thenReturn(room);
    }

    @Test
    void isHost_derived_and_displayName_resolved() {
        when(participantRepository.findByRoomIdOrderByJoinedAt(ROOM)).thenReturn(List.of(
            ChatParticipant.reconstitute(ROOM, HOST, Instant.parse("2026-06-01T10:00:00Z")),
            ChatParticipant.reconstitute(ROOM, GUEST, Instant.parse("2026-06-01T10:05:00Z"))
        ));
        when(userSummaryPort.batchGet(any())).thenReturn(Map.of(
            HOST, new UserSummaryView(HOST, "호스트"),
            GUEST, new UserSummaryView(GUEST, "게스트")
        ));

        List<ParticipantView> views = service.list(new ListParticipants(ROOM, HOST));

        assertThat(views).hasSize(2);
        assertThat(views.get(0).userId()).isEqualTo(HOST);
        assertThat(views.get(0).isHost()).isTrue();
        assertThat(views.get(0).displayName()).isEqualTo("호스트");
        assertThat(views.get(1).isHost()).isFalse();
        // N+1 회피 — 정확한 userId 집합으로 1회 batchGet.
        verify(userSummaryPort).batchGet(java.util.Set.of(HOST, GUEST));
    }

    @Test
    void missing_summary_falls_back_to_unknown() {
        when(participantRepository.findByRoomIdOrderByJoinedAt(ROOM)).thenReturn(List.of(
            ChatParticipant.reconstitute(ROOM, GUEST, Instant.parse("2026-06-01T10:05:00Z"))
        ));
        when(userSummaryPort.batchGet(any())).thenReturn(Map.of());  // NOT_FOUND

        List<ParticipantView> views = service.list(new ListParticipants(ROOM, HOST));

        assertThat(views).hasSize(1);
        assertThat(views.get(0).displayName()).isEqualTo(UserSummaryView.UNKNOWN_DISPLAY_NAME);
    }

    @Test
    void empty_room_returns_empty_without_summary_call() {
        when(participantRepository.findByRoomIdOrderByJoinedAt(ROOM)).thenReturn(List.of());
        List<ParticipantView> views = service.list(new ListParticipants(ROOM, HOST));
        assertThat(views).isEmpty();
        verify(userSummaryPort, never()).batchGet(any());  // 빈 방엔 gRPC 호출 안 함
    }
}
