package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.domain.model.diarychat.ChatParticipant;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatParticipantRepository;
import app.backend.jamo.diary.infrastructure.persistence.mapper.ChatParticipantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * ChatParticipantRepository port 구현.
 */
@Repository
@RequiredArgsConstructor
public class ChatParticipantRepositoryImpl implements ChatParticipantRepository {

    private final SpringDataChatParticipantRepository jpa;

    @Override
    public void save(ChatParticipant participant) {
        jpa.save(ChatParticipantMapper.toJpaEntity(participant));
    }

    @Override
    public boolean existsByRoomIdAndUserId(RoomId roomId, UUID userId) {
        return jpa.existsByRoomIdAndUserId(roomId.value(), userId);
    }

    @Override
    public List<ChatParticipant> findByRoomIdOrderByJoinedAt(RoomId roomId) {
        return jpa.findByRoomIdOrderByJoinedAtAsc(roomId.value()).stream()
            .map(ChatParticipantMapper::toDomain)
            .toList();
    }

    @Override
    public long countByRoomId(RoomId roomId) {
        return jpa.countByRoomId(roomId.value());
    }

    @Override
    public void deleteByRoomIdAndUserId(RoomId roomId, UUID userId) {
        jpa.deleteByRoomIdAndUserId(roomId.value(), userId);
    }
}
