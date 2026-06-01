package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.infrastructure.persistence.entity.ChatParticipantJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpringDataChatParticipantRepository extends JpaRepository<ChatParticipantJpaEntity, Long> {

    boolean existsByRoomIdAndUserId(Long roomId, UUID userId);

    List<ChatParticipantJpaEntity> findByRoomIdOrderByJoinedAtAsc(Long roomId);

    long countByRoomId(Long roomId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ChatParticipantJpaEntity p where p.roomId = :roomId and p.userId = :userId")
    int deleteByRoomIdAndUserId(@Param("roomId") Long roomId, @Param("userId") UUID userId);
}
