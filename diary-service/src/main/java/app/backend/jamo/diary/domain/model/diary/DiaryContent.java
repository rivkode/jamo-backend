package app.backend.jamo.diary.domain.model.diary;

import app.backend.jamo.diary.domain.exception.InvalidDiaryContentException;

import java.util.Objects;

/**
 * 일기 본문 VO.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §3.
 *
 * <p><b>길이 invariant</b>: 1..2000 <b>code points</b> ({@link String#codePointCount}). char (UTF-16 code unit)
 * 미사용 — 이모지 / 한자 surrogate pair 경계에서 글자 수 불일치 회피 (SentenceText 정합).
 *
 * <p><b>blank 차단</b>: 빈 문자열 또는 whitespace-only 입력은 invariant 위반. trim 미적용 — 사용자 입력 보존.
 *
 * <p>금칙어 / LLM 검증은 클라이언트 사전 호출 책임 (박제 §5) — 도메인 invariant X.
 */
public record DiaryContent(String value) {

    public static final int MAX_CODE_POINTS = 2000;

    public DiaryContent {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || isWhitespaceOnly(value)) {
            throw new InvalidDiaryContentException("content must not be blank");
        }
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints > MAX_CODE_POINTS) {
            throw new InvalidDiaryContentException(
                "content length out of range: max " + MAX_CODE_POINTS + " code points, got " + codePoints
            );
        }
    }

    private static boolean isWhitespaceOnly(String s) {
        return s.codePoints().allMatch(Character::isWhitespace);
    }
}
