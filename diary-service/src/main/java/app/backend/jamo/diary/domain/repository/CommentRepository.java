package app.backend.jamo.diary.domain.repository;

import app.backend.jamo.diary.domain.model.comment.Comment;
import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.repository.cursor.CommentCursor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Comment Aggregate Repository port.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §3 (hard-delete + cascade) / §6 (chronological cursor) /
 * §9 (CommentCreated Outbox) / §10 (DiaryDeleted / UserWithdrawal cascade — Saga 구독자 only).
 *
 * <p>구현체는 Infrastructure layer ({@code CommentRepositoryImpl}) — JpaEntity ↔ Domain Mapper 경유.
 *
 * <p><b>cleanup 메서드</b> ({@link #deleteAllByParentId} / {@link #deleteAllByDiaryId} /
 * {@link #deleteAllByAuthorId}): 호출자별로 의미가 다르다.
 * <ul>
 *   <li>{@link #deleteAllByParentId} — {@code DeleteCommentService} 가 답글 cascade 시 호출 (depth 1단 제한이라
 *   루트 댓글 삭제 시에만 의미).</li>
 *   <li>{@link #deleteAllByDiaryId} — {@code CommentOnDiaryDeletedListener} (DiaryDeleted Saga 구독자) 만
 *   호출 — 일반 Application Service 가 호출하지 않는다.</li>
 *   <li>{@link #deleteAllByAuthorId} — {@code UserWithdrawalRequestedListener} 가 회원 탈퇴 cascade 시 호출.</li>
 * </ul>
 *
 * <p>모두 멱등 — 존재하지 않는 ID 호출은 no-op (return 0). Saga 재시도 안전 (DiaryRepository 정합).
 */
public interface CommentRepository {

    /** 신규 / 갱신 모두 동일 메서드 (UPSERT) — Aggregate ID 가 영속 키. */
    void save(Comment comment);

    Optional<Comment> findById(CommentId id);

    boolean existsById(CommentId id);

    /** 멱등 hard-delete (박제 §3) — 존재하지 않는 ID 호출은 no-op (구현체 강제). */
    void deleteById(CommentId id);

    /**
     * 일기별 댓글 chronological 목록 — {@code WHERE diary_id = ?} ORDER BY {@code (created_at asc, comment_id asc)}.
     *
     * <p>박제 §6: 정렬은 chronological asc (대화 순서) 단일. flat list 로 답글 (parentId 보유) 도 포함 —
     * 클라이언트가 parentId 로 트리 구성 (서버측 트리 구조 미반환).
     *
     * @param diaryId      대상 일기
     * @param cursorOrNull null = 첫 페이지
     * @param limit        1..100 (size+1 fetch 패턴 — Application 이 limit+1 호출 후 hasNext 계산)
     */
    List<Comment> findByDiaryId(DiaryId diaryId, CommentCursor cursorOrNull, int limit);

    /**
     * 답글 cascade — 부모 댓글 삭제 시 자식 답글 일괄 hard-delete.
     *
     * <p>호출자: {@code DeleteCommentService}. 멱등 — 자식 없으면 no-op (return 0).
     *
     * <p><b>depth 1단 제한 가정</b>: 박제 §2 정합. 자식 댓글이 자체 자식을 가질 수 없으므로 단일 호출로 모든 cascade
     * 처리. 향후 depth 2단 이상으로 정책 변경 시 (현재 Non-Goals) 본 메서드는 재귀 cascade 또는 별도 시그니처로 재검토 필요.
     *
     * @return hard-delete 된 row 수
     */
    int deleteAllByParentId(CommentId parentId);

    /**
     * DiaryDeleted Saga cascade — {@code CommentOnDiaryDeletedListener} 가 호출. 박제 §10. 일반 Application
     * Service 가 호출하지 않는다.
     *
     * @return hard-delete 된 row 수
     */
    int deleteAllByDiaryId(DiaryId diaryId);

    /**
     * 회원 탈퇴 Saga cascade — sentence-feedback / DiaryRepository 정합 (Saga 구독자 only).
     *
     * <p>호출자: {@code UserWithdrawalRequestedListener} — {@code UserWithdrawalRequested} 구독 후 실행.
     * 처리 완료 시 {@code UserDataPurged (sourceService="diary")} 회신 발행 (기존 listener 확장).
     *
     * @return hard-delete 된 row 수
     */
    int deleteAllByAuthorId(UUID authorId);
}
