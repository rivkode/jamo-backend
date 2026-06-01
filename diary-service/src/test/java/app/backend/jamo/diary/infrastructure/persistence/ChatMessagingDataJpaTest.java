package app.backend.jamo.diary.infrastructure.persistence;

import app.backend.jamo.diary.domain.model.diarychat.ChatMessage;
import app.backend.jamo.diary.domain.model.diarychat.ChatRoomEvent;
import app.backend.jamo.diary.domain.model.diarychat.MessageAudioUrl;
import app.backend.jamo.diary.domain.model.diarychat.MessageText;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatMessageRepository;
import app.backend.jamo.diary.domain.repository.ChatRoomEventRepository;
import app.backend.jamo.diary.infrastructure.persistence.repository.ChatMessageRepositoryImpl;
import app.backend.jamo.diary.infrastructure.persistence.repository.ChatRoomEventRepositoryImpl;
import app.backend.jamo.diary.infrastructure.support.AbstractMySQLContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * diarychat 메시지/이벤트 repository 정합 — auto-inc id, before(desc)/after(asc) 페이징, maxEventId baseline.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ChatMessageRepositoryImpl.class, ChatRoomEventRepositoryImpl.class})
@ActiveProfiles("test")
class ChatMessagingDataJpaTest extends AbstractMySQLContainerTest {

    @Autowired private ChatMessageRepository messageRepository;
    @Autowired private ChatRoomEventRepository eventRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);
    private final RoomId room = RoomId.of(777);
    private final UUID user = UUID.randomUUID();

    private long saveMessage(String text) {
        return messageRepository.save(
            ChatMessage.userMessage(room, user, new MessageText(text), null, clock)).id().value();
    }

    @Test
    void messages_auto_increment_and_after_asc_paging() {
        long m1 = saveMessage("a");
        long m2 = saveMessage("b");
        long m3 = saveMessage("c");
        assertThat(m2).isGreaterThan(m1);
        assertThat(m3).isGreaterThan(m2);

        // after m1 → m2, m3 오름차순
        List<ChatMessage> after = messageRepository.findByRoomIdAfter(room, m1, 50);
        assertThat(after).extracting(m -> m.id().value()).containsExactly(m2, m3);
    }

    @Test
    void before_desc_paging_with_limit() {
        long m1 = saveMessage("a");
        long m2 = saveMessage("b");
        long m3 = saveMessage("c");

        // before 없음 → 최신부터 desc, limit 2 → m3, m2
        List<ChatMessage> latest = messageRepository.findByRoomIdBefore(room, null, 2);
        assertThat(latest).extracting(m -> m.id().value()).containsExactly(m3, m2);

        // before m3 → m2, m1
        List<ChatMessage> older = messageRepository.findByRoomIdBefore(
            room, app.backend.jamo.diary.domain.model.diarychat.MessageId.of(m3), 50);
        assertThat(older).extracting(m -> m.id().value()).containsExactly(m2, m1);
    }

    @Test
    void audio_url_persisted() {
        ChatMessage saved = messageRepository.save(ChatMessage.userMessage(
            room, user, new MessageText("음성"), new MessageAudioUrl("https://m.example.com/a.wav"), clock));
        List<ChatMessage> after = messageRepository.findByRoomIdAfter(room, saved.id().value() - 1, 10);
        assertThat(after.get(0).audioUrl()).contains("https://m.example.com/a.wav");
    }

    @Test
    void events_maxId_baseline_and_after() {
        assertThat(eventRepository.maxEventIdByRoomId(room)).isZero();  // 비어있으면 0

        eventRepository.append(ChatRoomEvent.participantJoined(room, user, clock));
        eventRepository.append(ChatRoomEvent.aiToggleChanged(room, user, true, clock));
        long max = eventRepository.maxEventIdByRoomId(room);
        assertThat(max).isPositive();

        eventRepository.append(ChatRoomEvent.participantLeft(room, user, clock));

        // baseline=max → 이후 발생한 left 이벤트만
        List<ChatRoomEvent> fresh = eventRepository.findByRoomIdAfter(room, max, 50);
        assertThat(fresh).hasSize(1);
        assertThat(fresh.get(0).type())
            .isEqualTo(app.backend.jamo.diary.domain.model.diarychat.ChatRoomEventType.PARTICIPANT_LEFT);
    }
}
