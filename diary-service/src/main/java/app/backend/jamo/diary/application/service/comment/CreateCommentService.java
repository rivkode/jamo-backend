package app.backend.jamo.diary.application.service.comment;

import app.backend.jamo.contracts.event.diary.CommentCreated;
import app.backend.jamo.diary.application.dto.comment.CommentView;
import app.backend.jamo.diary.application.dto.comment.CreateCommentCommand;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.exception.CommentNotFoundException;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.exception.InvalidCommentParentException;
import app.backend.jamo.diary.domain.model.comment.Comment;
import app.backend.jamo.diary.domain.model.comment.CommentContent;
import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.repository.CommentRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import app.backend.jamo.diary.domain.repository.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 댓글 작성 use case.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §2 (답글 깊이 1단) / §5 (비공개 일기 가드 — 404) /
 * §9 (CommentCreated Outbox) + diary-domain-policy.md §4 (Diary.commentCount 동기화 = Option A).
 *
 * <p><b>단일 트랜잭션</b>: Comment save + Outbox CommentCreated insert + Diary.onCommentAdded + Diary save.
 * UserSummary gRPC 호출은 트랜잭션 외부 (read-only, 응답 조립용). diary core CreateDiaryService 정합.
 *
 * <p>흐름:
 * <ol>
 *   <li>VO 생성 (CommentContent) — invariant 위반 시 도메인 예외 (400 매핑)</li>
 *   <li>트랜잭션 안:
 *     <ul>
 *       <li>Diary 조회 + {@code isAccessibleBy} 가드 → false 시 {@link DiaryNotFoundException} (404 IDOR)</li>
 *       <li>parentIdOrNull 비어있지 않으면 parent 조회 + {@code isReply()} 검증 →
 *           답글에 답글 시도 시 {@link InvalidCommentParentException} (400)</li>
 *       <li>parent.diaryId 가 본 diaryId 와 일치하지 않으면 다른 일기 댓글 위에 답글 시도 — 400 (정합 검증)</li>
 *       <li>Comment.create + save + Outbox CommentCreated insert + Diary.onCommentAdded + Diary save</li>
 *     </ul>
 *   </li>
 *   <li>트랜잭션 외부: UserSummary gRPC (단건) — fallback 처리 후 응답 조립</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CreateCommentService {

    private final CommentRepository commentRepository;
    private final DiaryRepository diaryRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final UserSummaryPort userSummaryPort;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public CommentView create(CreateCommentCommand command) {
        CommentContent content = new CommentContent(command.content());
        DiaryId diaryId = DiaryId.of(command.diaryId());
        CommentId parentId = command.parentIdOrNull() == null
            ? null
            : CommentId.of(command.parentIdOrNull());

        CommentId commentId = CommentId.newId();
        Comment saved = transactionTemplate.execute(status -> {
            // 1. Diary 가드 (404 IDOR)
            Diary diary = diaryRepository.findByIdForUpdate(diaryId)
                .orElseThrow(() -> new DiaryNotFoundException(
                    "diary not found: " + diaryId.asString()));
            if (!diary.isAccessibleBy(command.authorId())) {
                throw new DiaryNotFoundException("diary not found: " + diaryId.asString());
            }

            // 2. 답글 깊이 1단 검증 (Application 책임 — ddd-architect Q2)
            if (parentId != null) {
                Comment parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> new CommentNotFoundException(
                        "parent comment not found: " + parentId.asString()));
                if (parent.isReply()) {
                    throw new InvalidCommentParentException(
                        "parent_must_be_root: replies cannot have replies");
                }
                if (!parent.diaryId().equals(diaryId)) {
                    throw new InvalidCommentParentException(
                        "parent_diary_mismatch: parent comment belongs to a different diary");
                }
            }

            // 3. Comment 생성 + save + Outbox + Diary 카운터 갱신
            Comment comment = Comment.create(commentId, diaryId, command.authorId(), content, parentId, clock);
            commentRepository.save(comment);
            outboxEventPublisher.publish(new CommentCreated(
                UUID.randomUUID().toString(),
                Instant.now(clock),
                commentId.asString(),
                diaryId.asString(),
                command.authorId().toString()
            ));
            diary.onCommentAdded();
            diaryRepository.save(diary);
            return comment;
        });

        String displayName = UserSummaryView.displayNameOrUnknown(
            userSummaryPort.get(command.authorId()));

        return CommentView.from(saved, displayName, false);
    }
}
