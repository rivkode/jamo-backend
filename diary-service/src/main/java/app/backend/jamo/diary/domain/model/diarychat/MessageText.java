package app.backend.jamo.diary.domain.model.diarychat;

import app.backend.jamo.diary.domain.exception.InvalidChatMessageException;

/**
 * 채팅 메시지 본문 VO — 1..{@link #MAX_CODE_POINTS} code points, blank 금지.
 *
 * <p>박제 v2 §8-b: 채팅 단위라 validation 도메인(1..2000)보다 짧은 1..1000. 길이는 code point 기준
 * (이모지/surrogate 정합 — DiaryLines / SentenceText 정합).
 */
public record MessageText(String value) {

    public static final int MAX_CODE_POINTS = 1000;

    public MessageText {
        if (value == null || value.isBlank()) {
            throw new InvalidChatMessageException("message text must not be blank");
        }
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints > MAX_CODE_POINTS) {
            throw new InvalidChatMessageException(
                "message text exceeds " + MAX_CODE_POINTS + " code points: " + codePoints);
        }
    }
}
