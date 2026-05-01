package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.infrastructure.persistence.entity.DiaryJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataDiaryRepository extends JpaRepository<DiaryJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DiaryJpaEntity d where d.id = :id")
    Optional<DiaryJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DiaryJpaEntity d where d.authorId = :authorId")
    int deleteAllByAuthorId(@Param("authorId") UUID authorId);

    // ========================================================================
    // 공개 피드 RECENT — keyset 페이징.
    //
    // 첫 페이지: cursor null → WHERE visibility=PUBLIC ORDER BY created_at DESC, id DESC LIMIT ?
    // 다음 페이지: cursor 채움 → AND (created_at, id) < (lastCreatedAt, lastId)
    //
    // 태그 필터: JSON_CONTAINS(tags, '"<tag>"') — 인덱스 미활용 (정규화 미적용 박제 §6).
    // 인덱스 idx_diaries_visibility_created_at 가 visibility 우선 + created_at desc 정렬을 지원.
    // ========================================================================

    @Query(value = "SELECT * FROM diaries "
        + "WHERE visibility = 'PUBLIC' "
        + "ORDER BY created_at DESC, id DESC LIMIT :limit",
        nativeQuery = true)
    List<DiaryJpaEntity> findPublicFeedRecentFirst(@Param("limit") int limit);

    @Query(value = "SELECT * FROM diaries "
        + "WHERE visibility = 'PUBLIC' "
        + "AND (created_at, id) < (:lastCreatedAt, :lastId) "
        + "ORDER BY created_at DESC, id DESC LIMIT :limit",
        nativeQuery = true)
    List<DiaryJpaEntity> findPublicFeedRecentAfter(@Param("lastCreatedAt") Instant lastCreatedAt,
                                                   @Param("lastId") UUID lastId,
                                                   @Param("limit") int limit);

    @Query(value = "SELECT * FROM diaries "
        + "WHERE visibility = 'PUBLIC' "
        + "AND JSON_CONTAINS(tags, JSON_QUOTE(:tag)) = 1 "
        + "ORDER BY created_at DESC, id DESC LIMIT :limit",
        nativeQuery = true)
    List<DiaryJpaEntity> findPublicFeedRecentByTagFirst(@Param("tag") String tag,
                                                        @Param("limit") int limit);

    @Query(value = "SELECT * FROM diaries "
        + "WHERE visibility = 'PUBLIC' "
        + "AND JSON_CONTAINS(tags, JSON_QUOTE(:tag)) = 1 "
        + "AND (created_at, id) < (:lastCreatedAt, :lastId) "
        + "ORDER BY created_at DESC, id DESC LIMIT :limit",
        nativeQuery = true)
    List<DiaryJpaEntity> findPublicFeedRecentByTagAfter(@Param("tag") String tag,
                                                        @Param("lastCreatedAt") Instant lastCreatedAt,
                                                        @Param("lastId") UUID lastId,
                                                        @Param("limit") int limit);

    // ========================================================================
    // 공개 피드 POPULAR — (like_count, created_at, id) tiebreak.
    // ========================================================================

    @Query(value = "SELECT * FROM diaries "
        + "WHERE visibility = 'PUBLIC' "
        + "ORDER BY like_count DESC, created_at DESC, id DESC LIMIT :limit",
        nativeQuery = true)
    List<DiaryJpaEntity> findPublicFeedPopularFirst(@Param("limit") int limit);

    @Query(value = "SELECT * FROM diaries "
        + "WHERE visibility = 'PUBLIC' "
        + "AND (like_count, created_at, id) < (:lastLikeCount, :lastCreatedAt, :lastId) "
        + "ORDER BY like_count DESC, created_at DESC, id DESC LIMIT :limit",
        nativeQuery = true)
    List<DiaryJpaEntity> findPublicFeedPopularAfter(@Param("lastLikeCount") int lastLikeCount,
                                                    @Param("lastCreatedAt") Instant lastCreatedAt,
                                                    @Param("lastId") UUID lastId,
                                                    @Param("limit") int limit);

    @Query(value = "SELECT * FROM diaries "
        + "WHERE visibility = 'PUBLIC' "
        + "AND JSON_CONTAINS(tags, JSON_QUOTE(:tag)) = 1 "
        + "ORDER BY like_count DESC, created_at DESC, id DESC LIMIT :limit",
        nativeQuery = true)
    List<DiaryJpaEntity> findPublicFeedPopularByTagFirst(@Param("tag") String tag,
                                                         @Param("limit") int limit);

    @Query(value = "SELECT * FROM diaries "
        + "WHERE visibility = 'PUBLIC' "
        + "AND JSON_CONTAINS(tags, JSON_QUOTE(:tag)) = 1 "
        + "AND (like_count, created_at, id) < (:lastLikeCount, :lastCreatedAt, :lastId) "
        + "ORDER BY like_count DESC, created_at DESC, id DESC LIMIT :limit",
        nativeQuery = true)
    List<DiaryJpaEntity> findPublicFeedPopularByTagAfter(@Param("tag") String tag,
                                                         @Param("lastLikeCount") int lastLikeCount,
                                                         @Param("lastCreatedAt") Instant lastCreatedAt,
                                                         @Param("lastId") UUID lastId,
                                                         @Param("limit") int limit);

    // ========================================================================
    // 본인 피드 — visibility 무관 + RECENT only.
    // ========================================================================

    @Query(value = "SELECT * FROM diaries "
        + "WHERE author_id = :authorId "
        + "ORDER BY created_at DESC, id DESC LIMIT :limit",
        nativeQuery = true)
    List<DiaryJpaEntity> findMyFeedRecentFirst(@Param("authorId") UUID authorId,
                                               @Param("limit") int limit);

    @Query(value = "SELECT * FROM diaries "
        + "WHERE author_id = :authorId "
        + "AND (created_at, id) < (:lastCreatedAt, :lastId) "
        + "ORDER BY created_at DESC, id DESC LIMIT :limit",
        nativeQuery = true)
    List<DiaryJpaEntity> findMyFeedRecentAfter(@Param("authorId") UUID authorId,
                                               @Param("lastCreatedAt") Instant lastCreatedAt,
                                               @Param("lastId") UUID lastId,
                                               @Param("limit") int limit);
}
