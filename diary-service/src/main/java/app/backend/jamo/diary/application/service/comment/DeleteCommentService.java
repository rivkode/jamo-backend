package app.backend.jamo.diary.application.service.comment;

import app.backend.jamo.diary.application.dto.comment.DeleteCommentCommand;
import app.backend.jamo.diary.domain.exception.CommentNotFoundException;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.model.comment.Comment;
import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.repository.CommentLikeRepository;
import app.backend.jamo.diary.domain.repository.CommentRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 댓글 삭제 use case (hard-delete + 답글 cascade + 좋아요 cascade).
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §3 (hard-delete + cascade) / §4 (404 통일 IDOR) /
 * §10 (CommentDeleted 이벤트 미발행 — 알림 도메인 후속) + diary-domain-policy.md §4 (Diary.commentCount 동기화).
 *
 * <p><b>cascade 순서</b> (ddd-architect Q2 권고): 자식 좋아요 → 자식 댓글 → 본인 좋아요 → 본인 댓글. 외래 ID FK
 * 미사용이지만 의도 가독성 + 트랜잭션 롤백 시 일관 상태 유지. depth 1단 제한이라 cascade 단순.
 *
 * <p><b>Diary.commentCount 동기화</b>: 부모 + 자식 답글 N+1 회 호출 (depth 1단 제한이라 자식 5건 이내 일반적,
 * 사용자 결정). Diary.onCommentRemoved 가 카운터 0 미만 시 IllegalStateException — 정상 흐름에서는 발생 안 함
 * (drift 방지).
 *
 * <p>404 통일 (IDOR 보호):
 * <ul>
 *   <li>댓글 없음 → 404</li>
 *   <li>작성자 아님 → 404 (자원 존재 비노출, 박제 §4)</li>
 *   <li>비공개 일기 비작성자 → 404 (박제 §5)</li>
 *   <li>이미 삭제 (재호출) → 404 (비멱등, 박제 §3)</li>
 * </ul>
 *
 * <p>CommentDeleted 이벤트는 미발행 (박제 §10 — 알림 도메인 후속). DiaryDeleted Saga cascade 는 별도 Listener
 * (Slice 3 Infrastructure) 가 본 service 와 무관하게 처리.
 */
@Service
@RequiredArgsConstructor
public class DeleteCommentService {

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final DiaryRepository diaryRepository;
    private final TransactionTemplate transactionTemplate;

    public void delete(DeleteCommentCommand command) {
        CommentId commentId = CommentId.of(command.commentId());

        transactionTemplate.executeWithoutResult(status -> {
            Comment snapshot = commentRepository.findByIdWithoutLock(commentId)
                .orElseThrow(() -> new CommentNotFoundException(
                    "comment not found: " + commentId.asString()));

            // lock order: Diary -> Comment. snapshot 은 diaryId 확인용이며, lock 후 comment 를 재조회/재검증한다.
            Diary diary = diaryRepository.findByIdForUpdate(snapshot.diaryId())
                .orElseThrow(() -> new DiaryNotFoundException(
                    "diary not found: " + snapshot.diaryId().asString()));

            Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(
                    "comment not found: " + commentId.asString()));

            // 작성자 검증 — 작성자 아님 시 404 통일 (박제 §4 IDOR 보호)
            if (!comment.isOwnedBy(command.requesterId())) {
                throw new CommentNotFoundException(
                    "comment not found: " + commentId.asString());
            }

            // 비공개 일기 가드 (박제 §5) — 일기 자체 부재 또는 자기 댓글이라도 일기 visibility 변경 케이스 대응
            if (!diary.isAccessibleBy(command.requesterId())) {
                throw new DiaryNotFoundException(
                    "diary not found: " + comment.diaryId().asString());
            }

            int childCount = 0;
            if (comment.isRoot()) {
                // 자식 댓글 row lock → 자식 좋아요 → 자식 댓글. FK-less cascade 에서 자식 좋아요 orphan race 방지.
                List<Comment> children = commentRepository.findChildrenByParentIdForUpdate(commentId);
                commentLikeRepository.deleteAllByCommentParentId(commentId);
                commentRepository.deleteAllByParentId(commentId);
                childCount = children.size();
            }

            // 본인 좋아요 → 본인 댓글
            commentLikeRepository.deleteAllByCommentId(commentId);
            commentRepository.deleteById(commentId);

            // Diary.commentCount 동기화 — 부모 1 + 자식 N (depth 1단)
            int totalRemoved = 1 + childCount;
            for (int i = 0; i < totalRemoved; i++) {
                diary.onCommentRemoved();
            }
            diaryRepository.save(diary);
        });
    }
}
