package app.backend.jamo.diary.presentation.dto;

import java.util.Objects;

/**
 * diary core API JSON 오류 응답 표준 — code + generic message 만.
 *
 * <p>도메인 예외의 raw message / cause stack 은 클라이언트에 노출 금지 (sanitization 정책 정합 —
 * sentence-feedback {@code SentenceFeedbackErrorResponse} 패턴).
 */
public record DiaryErrorResponse(DiaryErrorCode code, String message) {

    public DiaryErrorResponse {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
