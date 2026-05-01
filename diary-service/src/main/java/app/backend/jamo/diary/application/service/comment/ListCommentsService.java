package app.backend.jamo.diary.application.service.comment;

import app.backend.jamo.diary.application.cursor.CommentCursorCodec;
import app.backend.jamo.diary.application.dto.comment.CommentListView;
import app.backend.jamo.diary.application.dto.comment.CommentView;
import app.backend.jamo.diary.application.dto.comment.ListCommentsQuery;
import app.backend.jamo.diary.application.port.UserSummaryPort;
import app.backend.jamo.diary.application.port.UserSummaryView;
import app.backend.jamo.diary.domain.exception.DiaryNotFoundException;
import app.backend.jamo.diary.domain.model.comment.Comment;
import app.backend.jamo.diary.domain.model.comment.CommentId;
import app.backend.jamo.diary.domain.model.diary.Diary;
import app.backend.jamo.diary.domain.model.diary.DiaryId;
import app.backend.jamo.diary.domain.repository.CommentLikeRepository;
import app.backend.jamo.diary.domain.repository.CommentRepository;
import app.backend.jamo.diary.domain.repository.DiaryRepository;
import app.backend.jamo.diary.domain.repository.cursor.CommentCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 댓글 목록 조회 use case.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §6 (chronological asc + size+1 fetch + flat list) /
 * §7 (응답 schema 9 필드 — UserSummary BatchGet 조립).
 *
 * <p><b>책임 재배치 (DiaryFeedCursorCodec / ListPublicFeedService 정합)</b>: cursor 는 raw String (nullable) 로
 * 받아 본 service 가 codec 호출. invariant 위반 시 {@code InvalidCommentCursorException} 그대로 전파 (400 매핑).
 *
 * <p>흐름:
 * <ol>
 *   <li>Diary 가드 (404 IDOR — 비공개 + 비작성자)</li>
 *   <li>cursor decode + size+1 fetch → hasNext 판정</li>
 *   <li>likedByMe 일괄 조회 (Set IN 절) + UserSummary BatchGet (작성자 ID 묶음)</li>
 *   <li>flat list 조립 — 클라이언트가 parentId 로 트리 구성 (서버측 미반환)</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ListCommentsService {

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final DiaryRepository diaryRepository;
    private final UserSummaryPort userSummaryPort;

    @Transactional(readOnly = true)
    public CommentListView list(ListCommentsQuery query) {
        DiaryId diaryId = DiaryId.of(query.diaryId());

        // 1. Diary 가드 (404 IDOR)
        Diary diary = diaryRepository.findById(diaryId)
            .orElseThrow(() -> new DiaryNotFoundException(
                "diary not found: " + diaryId.asString()));
        if (!diary.isAccessibleBy(query.viewerId())) {
            throw new DiaryNotFoundException("diary not found: " + diaryId.asString());
        }

        // 2. cursor decode + size+1 fetch
        CommentCursor cursor = (query.cursorOrNull() == null || query.cursorOrNull().isBlank())
            ? null
            : CommentCursorCodec.decode(query.cursorOrNull());
        int fetchLimit = query.size() + 1;
        List<Comment> fetched = commentRepository.findByDiaryId(diaryId, cursor, fetchLimit);

        boolean hasNext = fetched.size() > query.size();
        List<Comment> page = hasNext ? fetched.subList(0, query.size()) : fetched;

        // 3. likedByMe 일괄 조회
        Set<CommentId> commentIds = page.stream()
            .map(Comment::id)
            .collect(Collectors.toUnmodifiableSet());
        Set<CommentId> likedSet = commentIds.isEmpty()
            ? Set.of()
            : commentLikeRepository.findCommentIdsLikedByUser(query.viewerId(), commentIds);

        // 4. UserSummary BatchGet (작성자 ID 묶음)
        Set<UUID> authorIds = page.stream()
            .map(Comment::authorId)
            .collect(Collectors.toUnmodifiableSet());
        Map<UUID, UserSummaryView> summaries = authorIds.isEmpty()
            ? Map.of()
            : userSummaryPort.batchGet(authorIds);

        // 5. View 조립 (flat list)
        List<CommentView> items = page.stream()
            .map(c -> CommentView.from(
                c,
                UserSummaryView.displayNameOrUnknown(Optional.ofNullable(summaries.get(c.authorId()))),
                likedSet.contains(c.id())
            ))
            .toList();

        // 6. nextCursor 인코딩 (마지막 item 기반)
        String nextCursor = null;
        if (hasNext && !page.isEmpty()) {
            Comment last = page.get(page.size() - 1);
            nextCursor = CommentCursorCodec.encode(new CommentCursor(last.createdAt(), last.id()));
        }

        return new CommentListView(items, nextCursor, hasNext);
    }
}
