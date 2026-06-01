package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.domain.model.diarychat.ChatRoomEvent;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatRoomEventRepository;
import app.backend.jamo.diary.infrastructure.persistence.mapper.ChatRoomEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ChatRoomEventRepository port 구현.
 */
@Repository
@RequiredArgsConstructor
public class ChatRoomEventRepositoryImpl implements ChatRoomEventRepository {

    private final SpringDataChatRoomEventRepository jpa;

    @Override
    public void append(ChatRoomEvent event) {
        jpa.save(ChatRoomEventMapper.toJpaEntity(event));
    }

    @Override
    public long maxEventIdByRoomId(RoomId roomId) {
        return jpa.maxIdByRoomId(roomId.value());
    }

    @Override
    public List<ChatRoomEvent> findByRoomIdAfter(RoomId roomId, long afterEventId, int limit) {
        return jpa.findByRoomIdAndIdGreaterThanOrderByIdAsc(roomId.value(), afterEventId, PageRequest.of(0, limit))
            .stream().map(ChatRoomEventMapper::toDomain).toList();
    }
}
