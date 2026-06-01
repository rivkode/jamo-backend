package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;
import app.backend.jamo.diary.domain.repository.DiaryChatRoomRepository;
import app.backend.jamo.diary.infrastructure.persistence.mapper.DiaryChatRoomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * DiaryChatRoomRepository port 구현. 신규 저장 시 생성된 id 를 채운 도메인 반환 (late identity).
 */
@Repository
@RequiredArgsConstructor
public class DiaryChatRoomRepositoryImpl implements DiaryChatRoomRepository {

    private final SpringDataDiaryChatRoomRepository jpa;

    @Override
    public DiaryChatRoom save(DiaryChatRoom room) {
        return DiaryChatRoomMapper.toDomain(jpa.save(DiaryChatRoomMapper.toJpaEntity(room)));
    }

    @Override
    public Optional<DiaryChatRoom> findById(RoomId id) {
        return jpa.findById(id.value()).map(DiaryChatRoomMapper::toDomain);
    }

    @Override
    public Optional<DiaryChatRoom> findByDiaryId(UUID diaryId) {
        return jpa.findByDiaryId(diaryId).map(DiaryChatRoomMapper::toDomain);
    }
}
