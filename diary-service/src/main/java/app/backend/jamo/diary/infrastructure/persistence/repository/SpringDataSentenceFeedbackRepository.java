package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.infrastructure.persistence.entity.SentenceFeedbackJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataSentenceFeedbackRepository extends JpaRepository<SentenceFeedbackJpaEntity, UUID> {

    Optional<SentenceFeedbackJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SentenceFeedbackJpaEntity s where s.diaryId = :diaryId")
    int deleteAllByDiaryId(@Param("diaryId") UUID diaryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SentenceFeedbackJpaEntity s where s.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);

    /**
     * EXPIRED 전이 배치용 (D-a-5-impl-batch §3). MySQL 8 {@code FOR UPDATE SKIP LOCKED} —
     * 다중 인스턴스 동시 polling 시 같은 row 중복 처리 차단. id 만 native projection 으로 받아
     * 호출 측이 단건 트랜잭션 load + Aggregate.expire(clock) + save 진행.
     */
    @Query(value = "select id from sentence_feedback "
        + "where status = 'SUGGESTED' and expires_at < :cutoff "
        + "order by expires_at asc limit :batch for update skip locked",
        nativeQuery = true)
    List<byte[]> findExpirableSuggestedIdsForUpdate(@Param("cutoff") Instant cutoff,
                                                    @Param("batch") int batch);

    /**
     * 90일 보존 cleanup 용 (§14). final 상태 (ACCEPTED/REJECTED/EXPIRED/FAILED) + decided_at < cutoff.
     */
    @Query(value = "select id from sentence_feedback "
        + "where status in ('ACCEPTED','REJECTED','EXPIRED','FAILED') and decided_at < :cutoff "
        + "order by decided_at asc limit :batch for update skip locked",
        nativeQuery = true)
    List<byte[]> findFinalOlderThanIdsForUpdate(@Param("cutoff") Instant cutoff,
                                                @Param("batch") int batch);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SentenceFeedbackJpaEntity s where s.id in :ids")
    int deleteByIdIn(@Param("ids") List<UUID> ids);
}
