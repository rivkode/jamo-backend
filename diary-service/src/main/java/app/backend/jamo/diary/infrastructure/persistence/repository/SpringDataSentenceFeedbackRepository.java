package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.infrastructure.persistence.entity.SentenceFeedbackJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataSentenceFeedbackRepository extends JpaRepository<SentenceFeedbackJpaEntity, UUID> {

    Optional<SentenceFeedbackJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("delete from SentenceFeedbackJpaEntity s where s.diaryId = :diaryId")
    int deleteAllByDiaryId(@Param("diaryId") UUID diaryId);

    @Modifying
    @Query("delete from SentenceFeedbackJpaEntity s where s.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
