package app.backend.jamo.diary.domain.model.sentencefeedback;

import app.backend.jamo.diary.domain.exception.InvalidSentenceTextException;

import java.util.Objects;

/**
 * 문장 피드백의 입력 문장 VO.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §9 (50→200cp 확장, 2026-05-31 정정 —
 * 일기 3줄 lines 도입으로 sentence-feedback 이 라인(각 1..200cp) 단위 호출 정합).
 *
 * <p><b>길이 invariant</b>: 1..200 <b>code points</b> ({@link String#codePointCount}). 라인 1줄 = 1 문장
 * ({@code DiaryLines.LINE_MAX_CODE_POINTS} 정합). char (UTF-16 code unit) 미사용 — 이모지 / 한자
 * surrogate pair 경계에서 글자 수 불일치 회피.
 *
 * <p><b>blank 차단</b>: 빈 문자열 또는 whitespace-only 입력은 invariant 위반. trim 미적용
 * (사용자 입력 보존 — 정규화는 Application 또는 Presentation 책임). whitespace-only 만 차단.
 *
 * <p>금칙어 검증은 Application 슬라이스 (LLM 강제 검증 X — chat-service 가 룰 1차 검증).
 */
public record SentenceText(String value) {

    public static final int MAX_CODE_POINTS = 200;

    public SentenceText {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || isWhitespaceOnly(value)) {
            throw new InvalidSentenceTextException("sentence must not be blank");
        }
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints > MAX_CODE_POINTS) {
            throw new InvalidSentenceTextException(
                "sentence length out of range: max " + MAX_CODE_POINTS + " code points, got " + codePoints
            );
        }
    }

    private static boolean isWhitespaceOnly(String s) {
        return s.codePoints().allMatch(Character::isWhitespace);
    }
}
