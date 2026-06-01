package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.MessageCommands.ListMessages;
import app.backend.jamo.diary.application.dto.diarychat.MessageListView;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.domain.model.diarychat.ChatMessage;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.MessageId;
import app.backend.jamo.diary.domain.model.diarychat.MessageSource;
import app.backend.jamo.diary.domain.model.diarychat.MessageText;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListMessagesServiceTest {

    private static final RoomId ROOM = RoomId.of(1);
    private static final UUID USER = UUID.randomUUID();
    private static final UUID HOST = UUID.randomUUID();

    private ChatRoomAccessGuard accessGuard;
    private ChatMessageRepository messageRepository;
    private UserSummaryPort userSummaryPort;
    private ListMessagesService service;

    @BeforeEach
    void setUp() {
        accessGuard = mock(ChatRoomAccessGuard.class);
        messageRepository = mock(ChatMessageRepository.class);
        userSummaryPort = mock(UserSummaryPort.class);
        service = new ListMessagesService(accessGuard, messageRepository, new ChatMessageViewAssembler(userSummaryPort));
        DiaryChatRoom room = DiaryChatRoom.reconstitute(ROOM, UUID.randomUUID(), HOST, true,
            Instant.parse("2026-06-01T10:00:00Z"), null);
        when(accessGuard.loadAccessibleRoom(ROOM, USER)).thenReturn(room);
        when(userSummaryPort.batchGet(any())).thenReturn(Map.of());
    }

    /** id 내림차순 메시지 n건 (최근부터). */
    private List<ChatMessage> descMessages(int n, long startId) {
        return IntStream.range(0, n)
            .mapToObj(i -> ChatMessage.reconstitute(MessageId.of(startId - i), ROOM, USER,
                new MessageText("m" + i), null, MessageSource.USER, Instant.parse("2026-06-01T10:00:00Z")))
            .toList();
    }

    @Test
    void default_size_30_and_hasMore_when_extra_row() {
        // size 0 → 31 조회. 31건 반환 → hasMore true, 30건만 반환, oldest = 30번째 id.
        when(messageRepository.findByRoomIdBefore(eq(ROOM), isNull(), eq(31)))
            .thenReturn(descMessages(31, 100));

        MessageListView view = service.list(new ListMessages(ROOM, USER, null, 0));

        assertThat(view.items()).hasSize(30);
        assertThat(view.hasMore()).isTrue();
        assertThat(view.oldestMessageId()).isEqualTo(71);  // 100, 99, ... 71 (30번째)
    }

    @Test
    void no_more_when_fewer_than_size() {
        when(messageRepository.findByRoomIdBefore(eq(ROOM), isNull(), eq(31)))
            .thenReturn(descMessages(5, 50));

        MessageListView view = service.list(new ListMessages(ROOM, USER, null, 0));

        assertThat(view.items()).hasSize(5);
        assertThat(view.hasMore()).isFalse();
        assertThat(view.oldestMessageId()).isEqualTo(46);
    }

    @Test
    void empty_room_returns_null_oldest() {
        when(messageRepository.findByRoomIdBefore(eq(ROOM), isNull(), eq(31))).thenReturn(List.of());
        MessageListView view = service.list(new ListMessages(ROOM, USER, null, 0));
        assertThat(view.items()).isEmpty();
        assertThat(view.hasMore()).isFalse();
        assertThat(view.oldestMessageId()).isNull();
    }

    @Test
    void size_clamped_to_max_100() {
        when(messageRepository.findByRoomIdBefore(eq(ROOM), isNull(), anyInt())).thenReturn(descMessages(10, 10));
        service.list(new ListMessages(ROOM, USER, null, 500));

        // size 500 → 100 clamp → limit = size+1 = 101 로 조회 (명시 단언).
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(messageRepository).findByRoomIdBefore(eq(ROOM), isNull(), limit.capture());
        assertThat(limit.getValue()).isEqualTo(101);
    }

    @Test
    void negative_size_uses_default_30() {
        when(messageRepository.findByRoomIdBefore(eq(ROOM), isNull(), anyInt())).thenReturn(descMessages(3, 3));
        service.list(new ListMessages(ROOM, USER, null, -5));

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(messageRepository).findByRoomIdBefore(eq(ROOM), isNull(), limit.capture());
        assertThat(limit.getValue()).isEqualTo(31);  // default 30 + 1
    }
}
