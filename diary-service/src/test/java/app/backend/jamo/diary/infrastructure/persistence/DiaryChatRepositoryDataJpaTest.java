package app.backend.jamo.diary.infrastructure.persistence;

import app.backend.jamo.diary.domain.model.diarychat.ChatParticipant;
import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatParticipantRepository;
import app.backend.jamo.diary.domain.repository.DiaryChatRoomRepository;
import app.backend.jamo.diary.infrastructure.persistence.repository.ChatParticipantRepositoryImpl;
import app.backend.jamo.diary.infrastructure.persistence.repository.DiaryChatRoomRepositoryImpl;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * diarychat room/participant repository 정합 검증 — auto-increment id, findByDiaryId, 멱등 count/delete.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DiaryChatRoomRepositoryImpl.class, ChatParticipantRepositoryImpl.class})
@ActiveProfiles("test")
class DiaryChatRepositoryDataJpaTest extends AbstractMySQLContainerTest {

    @Autowired private DiaryChatRoomRepository roomRepository;
    @Autowired private ChatParticipantRepository participantRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void save_assigns_id_and_findByDiaryId_roundtrip() {
        UUID diaryId = UUID.randomUUID();
        UUID host = UUID.randomUUID();
        DiaryChatRoom saved = roomRepository.save(DiaryChatRoom.create(diaryId, host, true, clock));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.id().value()).isPositive();

        var found = roomRepository.findByDiaryId(diaryId);
        assertThat(found).isPresent();
        assertThat(found.get().id().value()).isEqualTo(saved.id().value());
        assertThat(found.get().hostUserId()).isEqualTo(host);
        assertThat(found.get().aiAssistantEnabled()).isTrue();
    }

    @Test
    void participant_exists_count_delete_idempotent() {
        DiaryChatRoom room = roomRepository.save(
            DiaryChatRoom.create(UUID.randomUUID(), UUID.randomUUID(), true, clock));
        RoomId roomId = room.id();
        UUID user = UUID.randomUUID();

        assertThat(participantRepository.existsByRoomIdAndUserId(roomId, user)).isFalse();

        participantRepository.save(ChatParticipant.join(roomId, user, clock));
        assertThat(participantRepository.existsByRoomIdAndUserId(roomId, user)).isTrue();
        assertThat(participantRepository.countByRoomId(roomId)).isEqualTo(1);

        // 다른 사용자 추가 → count 2, joinedAt 순서
        UUID user2 = UUID.randomUUID();
        participantRepository.save(ChatParticipant.join(roomId, user2,
            Clock.fixed(Instant.parse("2026-06-01T10:05:00Z"), ZoneOffset.UTC)));
        assertThat(participantRepository.countByRoomId(roomId)).isEqualTo(2);
        assertThat(participantRepository.findByRoomIdOrderByJoinedAt(roomId))
            .extracting(ChatParticipant::userId).containsExactly(user, user2);

        // 멱등 leave
        participantRepository.deleteByRoomIdAndUserId(roomId, user);
        assertThat(participantRepository.countByRoomId(roomId)).isEqualTo(1);
        participantRepository.deleteByRoomIdAndUserId(roomId, user);  // 재삭제 no-op
        assertThat(participantRepository.countByRoomId(roomId)).isEqualTo(1);
    }
}
