package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.PollView;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.domain.exception.ChatRoomNotFoundException;
import app.backend.jamo.diary.domain.model.diarychat.ChatMessage;
import app.backend.jamo.diary.domain.model.diarychat.ChatRoomEvent;
import app.backend.jamo.diary.domain.model.diarychat.ChatRoomEventType;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.MessageId;
import app.backend.jamo.diary.domain.model.diarychat.MessageSource;
import app.backend.jamo.diary.domain.model.diarychat.MessageText;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatMessageRepository;
import app.backend.jamo.diary.domain.repository.ChatRoomEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PollMessagesServiceTest {

    private static final RoomId ROOM = RoomId.of(1);
    private static final UUID USER = UUID.randomUUID();
    private static final UUID HOST = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

    private ChatRoomAccessGuard accessGuard;
    private ChatMessageRepository messageRepository;
    private ChatRoomEventRepository eventRepository;
    private UserSummaryPort userSummaryPort;
    private PollMessagesService service;

    @BeforeEach
    void setUp() {
        accessGuard = mock(ChatRoomAccessGuard.class);
        messageRepository = mock(ChatMessageRepository.class);
        eventRepository = mock(ChatRoomEventRepository.class);
        userSummaryPort = mock(UserSummaryPort.class);
        service = new PollMessagesService(accessGuard, messageRepository, eventRepository,
            new ChatMessageViewAssembler(userSummaryPort));
        when(userSummaryPort.batchGet(any())).thenReturn(Map.of());
    }

    @Test
    void beginPoll_checks_access_and_returns_baseline() {
        DiaryChatRoom room = DiaryChatRoom.reconstitute(ROOM, UUID.randomUUID(), HOST, true, CLOCK.instant(), null);
        when(accessGuard.loadAccessibleRoom(ROOM, USER)).thenReturn(room);
        when(eventRepository.maxEventIdByRoomId(ROOM)).thenReturn(7L);

        assertThat(service.beginPoll(ROOM, USER)).isEqualTo(7L);
    }

    @Test
    void beginPoll_propagates_guard_404() {
        when(accessGuard.loadAccessibleRoom(ROOM, USER)).thenThrow(new ChatRoomNotFoundException("x"));
        assertThatThrownBy(() -> service.beginPoll(ROOM, USER)).isInstanceOf(ChatRoomNotFoundException.class);
    }

    @Test
    void pollOnce_new_messages_set_nextAfter_to_max_id() {
        when(messageRepository.findByRoomIdAfter(eq(ROOM), eq(5L), anyInt())).thenReturn(List.of(
            ChatMessage.reconstitute(MessageId.of(6), ROOM, USER, new MessageText("a"), null, MessageSource.USER, CLOCK.instant()),
            ChatMessage.reconstitute(MessageId.of(7), ROOM, USER, new MessageText("b"), null, MessageSource.USER, CLOCK.instant())
        ));
        when(eventRepository.findByRoomIdAfter(eq(ROOM), eq(0L), anyInt())).thenReturn(List.of());

        PollView v = service.pollOnce(ROOM, 5L, 0L);

        assertThat(v.items()).hasSize(2);
        assertThat(v.nextAfter()).isEqualTo(7);
        assertThat(v.hasData()).isTrue();
    }

    @Test
    void pollOnce_only_events_keeps_nextAfter_as_after() {
        when(messageRepository.findByRoomIdAfter(eq(ROOM), eq(9L), anyInt())).thenReturn(List.of());
        when(eventRepository.findByRoomIdAfter(eq(ROOM), eq(3L), anyInt())).thenReturn(List.of(
            ChatRoomEvent.reconstitute(4L, ROOM, ChatRoomEventType.AI_TOGGLE_CHANGED, HOST, true, CLOCK.instant())
        ));

        PollView v = service.pollOnce(ROOM, 9L, 3L);

        assertThat(v.items()).isEmpty();
        assertThat(v.events()).hasSize(1);
        assertThat(v.events().get(0).type()).isEqualTo(ChatRoomEventType.AI_TOGGLE_CHANGED);
        assertThat(v.events().get(0).enabled()).isTrue();
        assertThat(v.nextAfter()).isEqualTo(9);  // 메시지 없으면 after 유지
        assertThat(v.hasData()).isTrue();
    }

    @Test
    void pollOnce_nothing_has_no_data() {
        when(messageRepository.findByRoomIdAfter(eq(ROOM), eq(9L), anyInt())).thenReturn(List.of());
        when(eventRepository.findByRoomIdAfter(eq(ROOM), eq(3L), anyInt())).thenReturn(List.of());

        PollView v = service.pollOnce(ROOM, 9L, 3L);

        assertThat(v.hasData()).isFalse();
        assertThat(v.nextAfter()).isEqualTo(9);
    }
}
