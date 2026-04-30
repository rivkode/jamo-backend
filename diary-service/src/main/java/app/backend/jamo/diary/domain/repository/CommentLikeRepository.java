package app.backend.jamo.diary.domain.repository;

import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.model.commentlike.CommentLike;
import app.backend.jamo.diary.domain.model.diary.DiaryId;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * CommentLike Aggregate Repository port.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §8 (좋아요 멱등 boolean) / §10 (DiaryDeleted /
 * UserWithdrawal cascade).
 *
 * <p><b>유니크 제약</b>: {@code (comment_id, user_id)} 동시 INSERT 차단. 멱등 UPSERT/DELETE 는 구현체에서
 * 동시성 안전 보장 (DB unique violation 또는 SELECT FOR UPDATE — DiaryLikeRepository 정합).
 *
 * <p><b>cleanup 메서드</b>: 호출자별로 의미가 다르다.
 * <ul>
 *   <li>{@link #deleteAllByCommentId} — {@code DeleteCommentService} 가 단일 댓글 삭제 cascade 시 호출.</li>
 *   <li>{@link #deleteAllByDiaryId} — {@code CommentOnDiaryDeletedListener} (DiaryDeleted Saga 구독자) 만
 *   호출 — 일반 Application Service 가 호출하지 않는다 (DiaryLikeRepository 정합, ddd-architect Q5 채택).</li>
 *   <li>{@link #deleteAllByUserId} — {@code UserWithdrawalRequestedListener} 가 회원 탈퇴 cascade 시 호출.</li>
 * </ul>
 *
 * <p>모두 멱등 — 존재하지 않는 ID 호출은 no-op (return 0). Saga 재시도 안전.
 */
public interface CommentLikeRepository {

    void save(CommentLike like);

    Optional<CommentLike> findByCommentIdAndUserId(CommentId commentId, UUID userId);

    boolean existsByCommentIdAndUserId(CommentId commentId, UUID userId);

    /** 멱등 — 존재하지 않는 row 삭제 시 no-op. */
    void deleteByCommentIdAndUserId(CommentId commentId, UUID userId);

    /**
     * 댓글 목록 응답의 {@code likedByMe} 일괄 조회 (N+1 회피, DiaryLikeRepository 정합).
     *
     * <p>입력 / 출력 모두 {@link Set} — 입력 중복은 자동 dedup, 출력은 입력의 부분집합. 호출자가 한 페이지
     * (size <= 100) 기준 사용 가정 — 100 초과 시 SQL {@code IN} 절 효율 저하 또는 DB 한계.
     *
     * @param userId     호출자
     * @param commentIds 한 페이지의 commentId 묶음 (size <= 100)
     * @return 호출자가 좋아요 누른 commentId 의 부분집합
     */
    Set<CommentId> findCommentIdsLikedByUser(UUID userId, Set<CommentId> commentIds);

    /**
     * 단일 댓글 삭제 cascade — {@code DeleteCommentService} 가 호출.
     *
     * @return hard-delete 된 row 수
     */
    int deleteAllByCommentId(CommentId commentId);

    /**
     * DiaryDeleted Saga cascade — {@code CommentOnDiaryDeletedListener} 가 호출. 박제 §10. 일반 Application
     * Service 가 호출하지 않는다.
     *
     * <p>구현체는 SQL JOIN ({@code DELETE FROM comment_likes WHERE comment_id IN (SELECT id FROM comments WHERE
     * diary_id = ?)}) 또는 {@code comment_likes.diary_id} 비정규화 컬럼 둘 다 흡수 가능 — port 시그니처는 무관.
     *
     * @return hard-delete 된 row 수
     */
    int deleteAllByDiaryId(DiaryId diaryId);

    /**
     * 회원 탈퇴 Saga cascade — 사용자 소유 모든 좋아요 row hard-delete (DiaryLikeRepository 정합).
     *
     * @return hard-delete 된 row 수
     */
    int deleteAllByUserId(UUID userId);
}
