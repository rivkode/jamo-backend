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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private AiAutoResponder aiAutoResponder;
    private final Executor synchronousExecutor = Runnable::run;  // afterCommit task 즉시 실행
    private SendMessageService service;

    @BeforeEach
    void setUp() {
        accessGuard = mock(ChatRoomAccessGuard.class);
        messageRepository = mock(ChatMessageRepository.class);
        userSummaryPort = mock(UserSummaryPort.class);
        aiAutoResponder = mock(AiAutoResponder.class);
        service = new SendMessageService(accessGuard, messageRepository,
            new ChatMessageViewAssembler(userSummaryPort), aiAutoResponder, synchronousExecutor, CLOCK);
        when(userSummaryPort.batchGet(any())).thenReturn(Map.of());
        // send() 가 트랜잭션 안에서 호출되는 것을 모사 — afterCommit 동기화 등록이 가능하도록 활성화.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void stubRoom(boolean aiEnabled) {
        DiaryChatRoom room = DiaryChatRoom.reconstitute(ROOM, UUID.randomUUID(), HOST, aiEnabled, CLOCK.instant(), null);
        when(accessGuard.loadAccessibleRoom(ROOM, SENDER)).thenReturn(room);
    }

    private void fireAfterCommit() {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
        }
    }

    @Test
    void send_persists_user_message_and_returns_view() {
        stubRoom(true);
        when(messageRepository.save(any())).thenReturn(ChatMessage.reconstitute(
            MessageId.of(42), ROOM, SENDER, new MessageText("안녕"), null, MessageSource.USER, CLOCK.instant()));

        MessageView view = service.send(new Send(ROOM, SENDER, "안녕", null));

        assertThat(view.messageId()).isEqualTo(42);
        assertThat(view.authorUserId()).isEqualTo(SENDER);
        assertThat(view.source()).isEqualTo(MessageSource.USER);
        assertThat(view.text()).isEqualTo("안녕");

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
        stubRoom(true);
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
        stubRoom(true);
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

    @Test
    void ai_enabled_room_triggers_responder_after_commit() {
        stubRoom(true);
        when(messageRepository.save(any())).thenReturn(ChatMessage.reconstitute(
            MessageId.of(42), ROOM, SENDER, new MessageText("안녕"), null, MessageSource.USER, CLOCK.instant()));

        service.send(new Send(ROOM, SENDER, "안녕", null));
        // 커밋 전에는 트리거되지 않음 — afterCommit 동기화 등록만.
        verify(aiAutoResponder, never()).respond(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());

        fireAfterCommit();
        verify(aiAutoResponder).respond(eq(ROOM), eq(SENDER), eq("안녕"), eq(42L));
    }

    @Test
    void ai_disabled_room_does_not_trigger_responder() {
        stubRoom(false);
        when(messageRepository.save(any())).thenReturn(ChatMessage.reconstitute(
            MessageId.of(42), ROOM, SENDER, new MessageText("안녕"), null, MessageSource.USER, CLOCK.instant()));

        service.send(new Send(ROOM, SENDER, "안녕", null));
        fireAfterCommit();

        verify(aiAutoResponder, never()).respond(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }
}
