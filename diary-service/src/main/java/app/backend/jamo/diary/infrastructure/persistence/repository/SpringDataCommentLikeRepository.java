package app.backend.jamo.diary.infrastructure.persistence.repository;

import app.backend.jamo.diary.infrastructure.persistence.entity.CommentLikeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataCommentLikeRepository extends JpaRepository<CommentLikeJpaEntity, UUID> {

    Optional<CommentLikeJpaEntity> findByCommentIdAndUserId(UUID commentId, UUID userId);

    boolean existsByCommentIdAndUserId(UUID commentId, UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CommentLikeJpaEntity l where l.commentId = :commentId and l.userId = :userId")
    int deleteByCommentIdAndUserIdReturnAffected(@Param("commentId") UUID commentId,
                                                 @Param("userId") UUID userId);

    @Query("select l.commentId from CommentLikeJpaEntity l "
        + "where l.userId = :userId and l.commentId in :commentIds")
    List<UUID> findLikedCommentIds(@Param("userId") UUID userId,
                                   @Param("commentIds") Collection<UUID> commentIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CommentLikeJpaEntity l where l.commentId = :commentId")
    int deleteAllByCommentId(@Param("commentId") UUID commentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE cl FROM comment_likes cl "
        + "JOIN comments c ON c.id = cl.comment_id "
        + "WHERE c.parent_id = :parentId",
        nativeQuery = true)
    int deleteAllByCommentParentId(@Param("parentId") UUID parentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE cl FROM comment_likes cl "
        + "JOIN comments c ON c.id = cl.comment_id "
        + "WHERE c.diary_id = :diaryId",
        nativeQuery = true)
    int deleteAllByDiaryId(@Param("diaryId") UUID diaryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CommentLikeJpaEntity l where l.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
