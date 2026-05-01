package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.infrastructure.persistence.entity.CommentJpaEntity;
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

public interface SpringDataCommentRepository extends JpaRepository<CommentJpaEntity, UUID> {

    /**
     * Comment likeCount 는 denormalized counter 이므로 쓰기 유스케이스에서 lost update 를 막기 위해
     * row-level write lock 으로 로드한다.
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CommentJpaEntity> findById(UUID id);

    @Query("select c from CommentJpaEntity c where c.id = :id")
    Optional<CommentJpaEntity> findByIdWithoutLock(@Param("id") UUID id);

    @Query(value = "SELECT * FROM comments "
        + "WHERE diary_id = :diaryId "
        + "ORDER BY created_at ASC, id ASC LIMIT :limit",
        nativeQuery = true)
    List<CommentJpaEntity> findByDiaryIdFirst(@Param("diaryId") UUID diaryId,
                                              @Param("limit") int limit);

    @Query(value = "SELECT * FROM comments "
        + "WHERE diary_id = :diaryId "
        + "AND (created_at, id) > (:lastCreatedAt, :lastId) "
        + "ORDER BY created_at ASC, id ASC LIMIT :limit",
        nativeQuery = true)
    List<CommentJpaEntity> findByDiaryIdAfter(@Param("diaryId") UUID diaryId,
                                              @Param("lastCreatedAt") Instant lastCreatedAt,
                                              @Param("lastId") UUID lastId,
                                              @Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CommentJpaEntity c where c.parentId = :parentId")
    List<CommentJpaEntity> findChildrenByParentIdForUpdate(@Param("parentId") UUID parentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CommentJpaEntity c where c.parentId = :parentId")
    int deleteAllByParentId(@Param("parentId") UUID parentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CommentJpaEntity c where c.diaryId = :diaryId")
    int deleteAllByDiaryId(@Param("diaryId") UUID diaryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CommentJpaEntity c where c.authorId = :authorId")
    int deleteAllByAuthorId(@Param("authorId") UUID authorId);
}
