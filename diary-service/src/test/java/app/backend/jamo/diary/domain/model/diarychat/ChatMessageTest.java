package app.backend.jamo.diary.domain.model.diarychat;

import app.backend.jamo.diary.domain.exception.InvalidChatMessageException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMessageTest {

    private static final RoomId ROOM = RoomId.of(1);
    private static final UUID USER = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void user_message_has_null_id_until_persisted_and_user_source() {
        ChatMessage m = ChatMessage.userMessage(ROOM, USER, new MessageText("안녕"), null, CLOCK);
        assertThat(m.id()).isNull();   // late identity
        assertThat(m.source()).isEqualTo(MessageSource.USER);
        assertThat(m.authorUserId()).contains(USER);
        assertThat(m.text()).isEqualTo("안녕");
        assertThat(m.audioUrl()).isEmpty();
    }

    @Test
    void user_message_with_audio_url() {
        ChatMessage m = ChatMessage.userMessage(ROOM, USER, new MessageText("음성"),
            new MessageAudioUrl("https://media.example.com/a.wav"), CLOCK);
        assertThat(m.audioUrl()).contains("https://media.example.com/a.wav");
    }

    @Test
    void blank_text_rejected() {
        assertThatThrownBy(() -> new MessageText("  "))
            .isInstanceOf(InvalidChatMessageException.class);
    }

    @Test
    void over_1000_code_points_rejected() {
        assertThatThrownBy(() -> new MessageText("가".repeat(1001)))
            .isInstanceOf(InvalidChatMessageException.class);
    }

    @Test
    void exactly_1000_accepted() {
        ChatMessage m = ChatMessage.userMessage(ROOM, USER, new MessageText("a".repeat(1000)), null, CLOCK);
        assertThat(m.text()).hasSize(1000);
    }

    @Test
    void audio_url_without_scheme_rejected() {
        assertThatThrownBy(() -> new MessageAudioUrl("media.example.com/a.wav"))
            .isInstanceOf(InvalidChatMessageException.class);
    }

    @Test
    void audio_url_with_userinfo_rejected() {
        assertThatThrownBy(() -> new MessageAudioUrl("https://user:pw@evil.com/a.wav"))
            .isInstanceOf(InvalidChatMessageException.class);
    }

    @Test
    void ai_message_has_no_author_no_audio_and_ai_source() {
        ChatMessage m = ChatMessage.aiMessage(ROOM, new MessageText("AI 응답입니다"), CLOCK);
        assertThat(m.id()).isNull();   // late identity
        assertThat(m.source()).isEqualTo(MessageSource.AI);
        assertThat(m.authorUserId()).isEmpty();
        assertThat(m.text()).isEqualTo("AI 응답입니다");
        assertThat(m.audioUrl()).isEmpty();
    }

    @Test
    void system_message_has_no_author_and_system_source() {
        ChatMessage m = ChatMessage.systemMessage(ROOM, new MessageText("AI 응답을 생성하지 못했어요."), CLOCK);
        assertThat(m.source()).isEqualTo(MessageSource.SYSTEM);
        assertThat(m.authorUserId()).isEmpty();
        assertThat(m.text()).isEqualTo("AI 응답을 생성하지 못했어요.");
    }
}
