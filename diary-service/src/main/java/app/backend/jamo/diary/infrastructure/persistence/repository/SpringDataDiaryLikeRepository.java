package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.infrastructure.persistence.entity.DiaryLikeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataDiaryLikeRepository extends JpaRepository<DiaryLikeJpaEntity, UUID> {

    Optional<DiaryLikeJpaEntity> findByDiaryIdAndUserId(UUID diaryId, UUID userId);

    boolean existsByDiaryIdAndUserId(UUID diaryId, UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DiaryLikeJpaEntity l where l.diaryId = :diaryId and l.userId = :userId")
    int deleteByDiaryIdAndUserIdReturnAffected(@Param("diaryId") UUID diaryId,
                                               @Param("userId") UUID userId);

    long countByDiaryId(UUID diaryId);

    /**
     * likedByMe 일괄 조회 — IN 절. 페이지 100건 한도 가정 (UserSummary Batch 정합).
     * 인덱스 idx_diary_likes_user_diary (user_id, diary_id) 활용.
     */
    @Query("select l.diaryId from DiaryLikeJpaEntity l "
        + "where l.userId = :userId and l.diaryId in :diaryIds")
    List<UUID> findLikedDiaryIds(@Param("userId") UUID userId,
                                 @Param("diaryIds") Collection<UUID> diaryIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DiaryLikeJpaEntity l where l.diaryId = :diaryId")
    int deleteAllByDiaryId(@Param("diaryId") UUID diaryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DiaryLikeJpaEntity l where l.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
