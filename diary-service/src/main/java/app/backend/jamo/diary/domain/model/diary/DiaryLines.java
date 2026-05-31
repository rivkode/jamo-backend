package app.backend.jamo.diary.domain.model.diary;

import app.backend.jamo.diary.domain.exception.InvalidLineCountException;
import app.backend.jamo.diary.domain.exception.InvalidLineLengthException;

import java.util.List;
import java.util.Objects;

/**
 * 일기 본문 VO — 정확히 3줄 (PRD 0526_flutter.md §2.3, "3줄 일기").
 *
 * <p>박제: decisions/diary/diary-domain-policy.md §3 (단일 content → lines 3줄 재설계).
 * 기존 {@code DiaryContent}(단일 1..2000cp) 대체.
 *
 * <p><b>invariant</b>:
 * <ul>
 *   <li>정확히 {@link #REQUIRED_SIZE}(3) 줄 — 위반 시 {@link InvalidLineCountException} (presentation 422)</li>
 *   <li>각 줄 1..{@link #LINE_MAX_CODE_POINTS}(200) code points + blank 차단 — 위반 시
 *       {@link InvalidLineLengthException} (presentation 400)</li>
 * </ul>
 *
 * <p><b>검증 순서</b> (ddd-architect): 개수(size==3)를 먼저, 그 다음 각 줄 길이. 3개 미만일 때 길이부터
 * 보면 엉뚱한 400 대신 422(개수 위반)가 정확히 노출되도록.
 *
 * <p><b>code points</b>: char (UTF-16) 미사용 — 이모지/한자 surrogate pair 경계 글자수 불일치 회피
 * (기존 DiaryContent / SentenceText / CommentContent 정합).
 *
 * <p><b>blank 차단</b>: 빈 문자열 또는 whitespace-only 줄은 invariant 위반. trim 미적용 — 사용자 입력 보존.
 *
 * <p>{@link List#copyOf} 불변 복사. Mapper 가 {@code line1/line2/line3} 3컬럼 ↔ {@link #values()}(3개)
 * 변환 — size==3 invariant 가 보장하므로 {@code values().get(0/1/2)} 안전.
 */
public record DiaryLines(List<String> values) {

    /** 일기는 정확히 3줄 (상한이 아닌 고정값 — PRD §2.3). */
    public static final int REQUIRED_SIZE = 3;

    /** 각 줄 최대 길이 (code points). */
    public static final int LINE_MAX_CODE_POINTS = 200;

    public DiaryLines {
        Objects.requireNonNull(values, "values");
        // 1. 개수 먼저 (size==3 위반 → 422)
        if (values.size() != REQUIRED_SIZE) {
            throw new InvalidLineCountException(
                "lines must be exactly " + REQUIRED_SIZE + ", got " + values.size());
        }
        // 2. 각 줄 길이 / blank (위반 → 400)
        for (int i = 0; i < values.size(); i++) {
            validateLine(i, values.get(i));
        }
        values = List.copyOf(values);
    }

    private static void validateLine(int index, String line) {
        if (line == null || line.isEmpty() || isWhitespaceOnly(line)) {
            throw new InvalidLineLengthException("line[" + index + "] must not be blank");
        }
        int codePoints = line.codePointCount(0, line.length());
        if (codePoints > LINE_MAX_CODE_POINTS) {
            throw new InvalidLineLengthException(
                "line[" + index + "] length out of range: max " + LINE_MAX_CODE_POINTS
                    + " code points, got " + codePoints);
        }
    }

    private static boolean isWhitespaceOnly(String s) {
        return s.codePoints().allMatch(Character::isWhitespace);
    }
}
