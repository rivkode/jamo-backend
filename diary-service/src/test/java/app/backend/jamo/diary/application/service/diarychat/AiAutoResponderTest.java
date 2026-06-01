package app.backend.jamo.diary.application.service.diarychat;

import app.backend.jamo.diary.domain.model.diarychat.ChatMessage;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.MessageId;
import app.backend.jamo.diary.domain.model.diarychat.MessageSource;
import app.backend.jamo.diary.domain.model.diarychat.MessageText;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatMessageRepository;
import app.backend.jamo.diary.domain.repository.DiaryChatAiGateway;
import app.backend.jamo.diary.domain.repository.DiaryChatRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAutoResponderTest {

    private static final RoomId ROOM = RoomId.of(1);
    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID HOST = UUID.randomUUID();
    private static final long TRIGGER_MSG_ID = 42L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

    private DiaryChatRoomRepository roomRepository;
    private ChatMessageRepository messageRepository;
    private DiaryChatAiGateway aiGateway;
    private AiAutoResponder responder;

    @BeforeEach
    void setUp() {
        roomRepository = mock(DiaryChatRoomRepository.class);
        messageRepository = mock(ChatMessageRepository.class);
        aiGateway = mock(DiaryChatAiGateway.class);
        responder = new AiAutoResponder(roomRepository, messageRepository, aiGateway, CLOCK);
    }

    private void stubRoom(boolean aiEnabled, boolean deleted) {
        Instant deletedAt = deleted ? CLOCK.instant() : null;
        when(roomRepository.findById(ROOM)).thenReturn(Optional.of(
            DiaryChatRoom.reconstitute(ROOM, UUID.randomUUID(), HOST, aiEnabled, CLOCK.instant(), deletedAt)));
        when(messageRepository.findByRoomIdBefore(eq(ROOM), any(MessageId.class), anyInt()))
            .thenReturn(List.of());
    }

    @Test
    void ok_result_stored_as_ai_message() {
        stubRoom(true, false);
        when(aiGateway.generate(any())).thenReturn(DiaryChatAiGateway.Result.ok("좋은 하루였네요!"));

        responder.respond(ROOM, SENDER, "오늘 날씨가 좋았어", TRIGGER_MSG_ID);

        ChatMessage saved = captureSaved();
        assertThat(saved.source()).isEqualTo(MessageSource.AI);
        assertThat(saved.authorUserId()).isEmpty();
        assertThat(saved.text()).isEqualTo("좋은 하루였네요!");
        assertThat(saved.roomId()).isEqualTo(ROOM);
    }

    @Test
    void over_limit_completion_is_truncated() {
        stubRoom(true, false);
        String tooLong = "가".repeat(MessageText.MAX_CODE_POINTS + 50);
        when(aiGateway.generate(any())).thenReturn(DiaryChatAiGateway.Result.ok(tooLong));

        responder.respond(ROOM, SENDER, "안녕", TRIGGER_MSG_ID);

        ChatMessage saved = captureSaved();
        assertThat(saved.text().codePointCount(0, saved.text().length()))
            .isEqualTo(MessageText.MAX_CODE_POINTS);
    }

    @Test
    void ok_but_blank_text_falls_back_to_system_message() {
        // OK 인데 본문이 공백뿐인 극단 edge — MessageText 불변식 위반으로 침묵하지 않고 SYSTEM 폴백 (code-reviewer H2).
        stubRoom(true, false);
        when(aiGateway.generate(any())).thenReturn(DiaryChatAiGateway.Result.ok("   "));

        responder.respond(ROOM, SENDER, "안녕", TRIGGER_MSG_ID);

        ChatMessage saved = captureSaved();
        assertThat(saved.source()).isEqualTo(MessageSource.SYSTEM);
        assertThat(saved.text()).isEqualTo(AiAutoResponder.MSG_FAILED);
    }

    @Test
    void failed_result_stored_as_system_message() {
        stubRoom(true, false);
        when(aiGateway.generate(any())).thenReturn(DiaryChatAiGateway.Result.failed());

        responder.respond(ROOM, SENDER, "안녕", TRIGGER_MSG_ID);

        ChatMessage saved = captureSaved();
        assertThat(saved.source()).isEqualTo(MessageSource.SYSTEM);
        assertThat(saved.text()).isEqualTo(AiAutoResponder.MSG_FAILED);
    }

    @Test
    void rate_limited_result_stored_as_system_message() {
        stubRoom(true, false);
        when(aiGateway.generate(any())).thenReturn(DiaryChatAiGateway.Result.rateLimited());

        responder.respond(ROOM, SENDER, "안녕", TRIGGER_MSG_ID);

        ChatMessage saved = captureSaved();
        assertThat(saved.source()).isEqualTo(MessageSource.SYSTEM);
        assertThat(saved.text()).isEqualTo(AiAutoResponder.MSG_RATE_LIMITED);
    }

    @Test
    void room_absent_skips_generation() {
        when(roomRepository.findById(ROOM)).thenReturn(Optional.empty());

        responder.respond(ROOM, SENDER, "안녕", TRIGGER_MSG_ID);

        verify(aiGateway, never()).generate(any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void ai_disabled_room_skips_generation() {
        stubRoom(false, false);

        responder.respond(ROOM, SENDER, "안녕", TRIGGER_MSG_ID);

        verify(aiGateway, never()).generate(any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void deleted_room_skips_generation() {
        stubRoom(true, true);

        responder.respond(ROOM, SENDER, "안녕", TRIGGER_MSG_ID);

        verify(aiGateway, never()).generate(any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void context_passed_in_chronological_order_with_roles() {
        when(roomRepository.findById(ROOM)).thenReturn(Optional.of(
            DiaryChatRoom.reconstitute(ROOM, UUID.randomUUID(), HOST, true, CLOCK.instant(), null)));
        // findByRoomIdBefore 는 내림차순(최신 우선) — responder 가 시간순으로 뒤집어 전달해야 함.
        ChatMessage newer = ChatMessage.reconstitute(MessageId.of(41), ROOM, null,
            new MessageText("이전 AI 응답"), null, MessageSource.AI, CLOCK.instant());
        ChatMessage older = ChatMessage.reconstitute(MessageId.of(40), ROOM, SENDER,
            new MessageText("이전 사용자 발화"), null, MessageSource.USER, CLOCK.instant());
        when(messageRepository.findByRoomIdBefore(eq(ROOM), any(MessageId.class), anyInt()))
            .thenReturn(List.of(newer, older));  // desc
        when(aiGateway.generate(any())).thenReturn(DiaryChatAiGateway.Result.ok("응답"));

        responder.respond(ROOM, SENDER, "현재 메시지", TRIGGER_MSG_ID);

        ArgumentCaptor<DiaryChatAiGateway.Args> captor =
            ArgumentCaptor.forClass(DiaryChatAiGateway.Args.class);
        verify(aiGateway).generate(captor.capture());
        DiaryChatAiGateway.Args args = captor.getValue();
        assertThat(args.userMessage()).isEqualTo("현재 메시지");
        assertThat(args.userId()).isEqualTo(SENDER);
        assertThat(args.recentMessages()).containsExactly(
            new DiaryChatAiGateway.RecentMessage("user", "이전 사용자 발화"),
            new DiaryChatAiGateway.RecentMessage("assistant", "이전 AI 응답"));
    }

    private ChatMessage captureSaved() {
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).save(captor.capture());
        return captor.getValue();
    }
}
