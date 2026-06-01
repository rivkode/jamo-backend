package app.backend.jamo.diary.domain.repository;

import app.backend.jamo.diary.domain.model.diarychat.DiaryChatRoom;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;

import java.util.Optional;
import java.util.UUID;

/**
 * DiaryChatRoom Aggregate Repository port.
 */
public interface DiaryChatRoomRepository {

    /** 신규(id 미정) 저장 시 생성 키를 채운 인스턴스 반환 (late identity), 갱신은 그대로. */
    DiaryChatRoom save(DiaryChatRoom room);

    Optional<DiaryChatRoom> findById(RoomId id);

    /** 일기당 1방 멱등 createOrGet — 기존 방 조회. */
    Optional<DiaryChatRoom> findByDiaryId(UUID diaryId);
}
