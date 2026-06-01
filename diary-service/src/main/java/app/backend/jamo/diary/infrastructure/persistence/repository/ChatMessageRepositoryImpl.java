package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.domain.model.diarychat.ChatMessage;
import app.backend.jamo.diary.domain.model.diarychat.MessageId;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.ChatMessageRepository;
import app.backend.jamo.diary.infrastructure.persistence.entity.ChatMessageJpaEntity;
import app.backend.jamo.diary.infrastructure.persistence.mapper.ChatMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ChatMessageRepository port 구현.
 */
@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepository {

    private final SpringDataChatMessageRepository jpa;

    @Override
    public ChatMessage save(ChatMessage message) {
        return ChatMessageMapper.toDomain(jpa.save(ChatMessageMapper.toJpaEntity(message)));
    }

    @Override
    public List<ChatMessage> findByRoomIdBefore(RoomId roomId, MessageId beforeOrNull, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<ChatMessageJpaEntity> rows = (beforeOrNull == null)
            ? jpa.findByRoomIdOrderByIdDesc(roomId.value(), page)
            : jpa.findByRoomIdAndIdLessThanOrderByIdDesc(roomId.value(), beforeOrNull.value(), page);
        return rows.stream().map(ChatMessageMapper::toDomain).toList();
    }

    @Override
    public List<ChatMessage> findByRoomIdAfter(RoomId roomId, long after, int limit) {
        return jpa.findByRoomIdAndIdGreaterThanOrderByIdAsc(roomId.value(), after, PageRequest.of(0, limit)).stream()
            .map(ChatMessageMapper::toDomain)
            .toList();
    }
}
