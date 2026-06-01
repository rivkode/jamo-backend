package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.application.dto.diarychat.MessageCommands.Send;
import app.backend.jamo.diary.application.dto.diarychat.MessageView;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.domain.exception.ChatRoomNotFoundException;
import app.backend.jamo.diary.domain.exception.InvalidChatMessageException;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SendMessageServiceTest {

    private static final RoomId ROOM = RoomId.of(1);
    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID HOST = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

    private ChatRoomAccessGuard accessGuard;
    private ChatMessageRepository messageRepository;
    private UserSummaryPort userSummaryPort;
    private SendMessageService service;

    @BeforeEach
    void setUp() {
        accessGuard = mock(ChatRoomAccessGuard.class);
        messageRepository = mock(ChatMessageRepository.class);
        userSummaryPort = mock(UserSummaryPort.class);
        service = new SendMessageService(accessGuard, messageRepository,
            new ChatMessageViewAssembler(userSummaryPort), CLOCK);
        DiaryChatRoom room = DiaryChatRoom.reconstitute(ROOM, UUID.randomUUID(), HOST, true, CLOCK.instant(), null);
        when(accessGuard.loadAccessibleRoom(ROOM, SENDER)).thenReturn(room);
        when(userSummaryPort.batchGet(any())).thenReturn(Map.of());
    }

    @Test
    void send_persists_user_message_and_returns_view() {
        when(messageRepository.save(any())).thenReturn(ChatMessage.reconstitute(
            MessageId.of(42), ROOM, SENDER, new MessageText("안녕"), null, MessageSource.USER, CLOCK.instant()));

        MessageView view = service.send(new Send(ROOM, SENDER, "안녕", null));

        assertThat(view.messageId()).isEqualTo(42);
        assertThat(view.authorUserId()).isEqualTo(SENDER);
        assertThat(view.source()).isEqualTo(MessageSource.USER);
        assertThat(view.text()).isEqualTo("안녕");

        // 저장된 도메인 객체가 command 대로 매핑되는지 (느슨한 any() 회피).
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).save(captor.capture());
        ChatMessage persisted = captor.getValue();
        assertThat(persisted.source()).isEqualTo(MessageSource.USER);
        assertThat(persisted.roomId()).isEqualTo(ROOM);
        assertThat(persisted.authorUserId()).contains(SENDER);
        assertThat(persisted.text()).isEqualTo("안녕");
        assertThat(persisted.audioUrl()).isEmpty();
    }

    @Test
    void send_with_audio_url_persists_audio() {
        when(messageRepository.save(any())).thenReturn(ChatMessage.reconstitute(
            MessageId.of(43), ROOM, SENDER, new MessageText("음성"),
            new app.backend.jamo.diary.domain.model.diarychat.MessageAudioUrl("https://m.example.com/a.wav"),
            MessageSource.USER, CLOCK.instant()));

        service.send(new Send(ROOM, SENDER, "음성", "https://m.example.com/a.wav"));

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().audioUrl()).contains("https://m.example.com/a.wav");
    }

    @Test
    void blank_text_rejected_without_save() {
        assertThatThrownBy(() -> service.send(new Send(ROOM, SENDER, "  ", null)))
            .isInstanceOf(InvalidChatMessageException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void guard_404_propagates_without_save() {
        when(accessGuard.loadAccessibleRoom(ROOM, SENDER)).thenThrow(new ChatRoomNotFoundException("x"));
        assertThatThrownBy(() -> service.send(new Send(ROOM, SENDER, "안녕", null)))
            .isInstanceOf(ChatRoomNotFoundException.class);
        verify(messageRepository, never()).save(any());
    }
}
