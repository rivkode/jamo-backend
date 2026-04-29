package app.backend.jamo.diary.domain.model.diary;

import app.backend.jamo.diary.domain.exception.InvalidTagException;

import java.util.Objects;

/**
 * 단일 태그 VO.
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §3 / §6 (free-form 단일 어휘 {@code tag}).
 *
 * <p><b>길이 invariant</b>: 1..30 <b>code points</b>. blank 차단.
 *
 * <p>정규화 (case fold / trim) 미적용 — 사용자 입력 그대로 보존. 검색은 단일 태그 매칭 (§6 multi-tag intersection 후속).
 */
public record Tag(String value) {

    public static final int MAX_CODE_POINTS = 30;

    public Tag {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || isWhitespaceOnly(value)) {
            throw new InvalidTagException("tag must not be blank");
        }
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints > MAX_CODE_POINTS) {
            throw new InvalidTagException(
                "tag length out of range: max " + MAX_CODE_POINTS + " code points, got " + codePoints
            );
        }
    }

    private static boolean isWhitespaceOnly(String s) {
        return s.codePoints().allMatch(Character::isWhitespace);
    }
}
