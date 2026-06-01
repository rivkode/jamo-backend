package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.infrastructure.persistence.entity.ChatMessageJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataChatMessageRepository extends JpaRepository<ChatMessageJpaEntity, Long> {

    /** 최신부터 (before 없음). */
    List<ChatMessageJpaEntity> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    /** before 미만 최신부터 (과거 페이지). */
    List<ChatMessageJpaEntity> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long before, Pageable pageable);

    /** after 초과 오름차순 (롱폴 새 메시지). */
    List<ChatMessageJpaEntity> findByRoomIdAndIdGreaterThanOrderByIdAsc(Long roomId, Long after, Pageable pageable);
}
