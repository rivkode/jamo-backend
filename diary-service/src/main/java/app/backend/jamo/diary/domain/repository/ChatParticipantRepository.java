package app.backend.jamo.diary.domain.repository;

import app.backend.jamo.diary.domain.model.diarychat.ChatParticipant;
import app.backend.jamo.diary.domain.model.diarychat.RoomId;

import java.util.List;
import java.util.UUID;

/**
 * ChatParticipant Repository port.
 */
public interface ChatParticipantRepository {

    /** 멱등 join — 이미 존재하면 no-op (unique(room_id,user_id) 보루). */
    void save(ChatParticipant participant);

    boolean existsByRoomIdAndUserId(RoomId roomId, UUID userId);

    /** 참여자 목록 — joinedAt 오름차순 (가입 순). */
    List<ChatParticipant> findByRoomIdOrderByJoinedAt(RoomId roomId);

    long countByRoomId(RoomId roomId);

    /** 멱등 leave — 없는 행 삭제도 안전 (no-op). 실제 삭제된 행 수 반환 (이벤트 append 조건 판단용). */
    int deleteByRoomIdAndUserId(RoomId roomId, UUID userId);
}
