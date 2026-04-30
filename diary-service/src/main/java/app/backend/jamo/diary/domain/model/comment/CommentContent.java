package app.backend.jamo.diary.domain.model.comment;

import app.backend.jamo.diary.domain.exception.InvalidCommentContentException;

import java.util.Objects;

/**
 * 댓글 본문 VO.
 *
 * <p>박제: decisions/diary/comment-domain-policy.md (CommentContent 500cp, 사용자 결정 — diary 본문 2000cp 의 1/4).
 *
 * <p><b>길이 invariant</b>: 1..500 <b>code points</b> ({@link String#codePointCount}). char (UTF-16 code unit)
 * 미사용 — 이모지 / 한자 surrogate pair 경계에서 글자 수 불일치 회피 ({@code DiaryContent} / {@code SentenceText} 정합).
 *
 * <p><b>blank 차단</b>: 빈 문자열 또는 whitespace-only 입력은 invariant 위반. trim 미적용 — 사용자 입력 보존
 * ({@code DiaryContent} / {@code Suggestion} 정합).
 */
public record CommentContent(String value) {

    public static final int MAX_CODE_POINTS = 500;

    public CommentContent {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || isWhitespaceOnly(value)) {
            throw new InvalidCommentContentException("content must not be blank");
        }
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints > MAX_CODE_POINTS) {
            throw new InvalidCommentContentException(
                "content length out of range: max " + MAX_CODE_POINTS + " code points, got " + codePoints
            );
        }
    }

    private static boolean isWhitespaceOnly(String s) {
        return s.codePoints().allMatch(Character::isWhitespace);
    }
}
