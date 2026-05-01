package app.backend.jamo.diary.application.dto.comment;

import java.util.Objects;
import java.util.UUID;

/**
 * 댓글 작성 use case 입력.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §1 (UUID 일관) / §2 (답글 깊이 1단 — Application 검증) /
 * §5 (비공개 일기 가드 — Application).
 *
 * <p>도메인 invariant 검증 (CommentContent 1..500cp + blank 차단) 은 VO 생성 시점에 위임. 본 record 는 단순 운반.
 *
 * @param diaryId        대상 일기
 * @param authorId       작성자 (path param 의 viewer 와 동일)
 * @param content        raw 본문 — VO 생성 시 invariant 검증 → InvalidCommentContentException → 400
 * @param parentIdOrNull null = 루트 댓글, non-null = 1단 답글 (depth 검증은 Service 책임)
 */
public record CreateCommentCommand(
    UUID diaryId,
    UUID authorId,
    String content,
    UUID parentIdOrNull
) {

    public CreateCommentCommand {
        Objects.requireNonNull(diaryId, "diaryId");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(content, "content");
        // parentIdOrNull 은 nullable 허용
    }
}
