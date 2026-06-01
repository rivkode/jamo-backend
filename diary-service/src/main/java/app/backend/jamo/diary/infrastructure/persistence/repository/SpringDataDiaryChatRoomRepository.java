package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.infrastructure.persistence.entity.DiaryChatRoomJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataDiaryChatRoomRepository extends JpaRepository<DiaryChatRoomJpaEntity, Long> {

    Optional<DiaryChatRoomJpaEntity> findByDiaryId(UUID diaryId);
}
