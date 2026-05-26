package app.backend.jamo.diary.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/diaries/{diaryId}/comments Request body.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md §2 (답글 깊이 1단 — Application 검증) /
 * comment-domain-policy.md (CommentContent 500cp).
 *
 * <p>PRD 0526_flutter.md §3.2 body: {@code {"text":"...1~500자", "parentCommentId":null}}.
 *
 * <p>Bean Validation 1차 — char (UTF-16) 기준 상한은 도메인 code points 한도의 ×2 (surrogate pair 대비)
 * 로 빠른 거부. 도메인 VO ({@code CommentContent}) 는 trim 미적용 / raw 보존 정책 (사용자 입력 그대로) +
 * 1..500 code points 정확한 invariant 검증 (code-reviewer M2).
 *
 * <p>{@code parentCommentId} 는 nullable raw String — Controller 가 UUID 로 변환 (실패 시 IAE → 400).
 * {@link #PARENT_ID_MAX_CHARS} 는 표준 UUID 문자열 길이 (36) — 거대한 페이로드 1차 거부 (code-reviewer M1).
 * 빈 문자열 / blank 는 Controller 에서 null 동의어로 처리 (루트 댓글).
 *
 * @param text             댓글 본문 (1..1000 char 1차, 도메인 1..500 cp)
 * @param parentCommentId  부모 댓글 UUID 문자열, null/blank = 루트 댓글, max 36 char
 */
public record CreateCommentRequest(
    @NotBlank
    @Size(max = TEXT_MAX_CHARS, message = "text too long")
    String text,

    @Size(max = PARENT_ID_MAX_CHARS, message = "parentCommentId too long")
    String parentCommentId
) {
    public static final int TEXT_MAX_CHARS = 1000;
    public static final int PARENT_ID_MAX_CHARS = 36;
}
