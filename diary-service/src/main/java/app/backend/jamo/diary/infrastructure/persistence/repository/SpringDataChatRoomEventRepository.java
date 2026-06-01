package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.infrastructure.persistence.entity.ChatRoomEventJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpringDataChatRoomEventRepository extends JpaRepository<ChatRoomEventJpaEntity, Long> {

    @Query("select coalesce(max(e.id), 0) from ChatRoomEventJpaEntity e where e.roomId = :roomId")
    long maxIdByRoomId(@Param("roomId") Long roomId);

    List<ChatRoomEventJpaEntity> findByRoomIdAndIdGreaterThanOrderByIdAsc(Long roomId, Long after, Pageable pageable);
}
