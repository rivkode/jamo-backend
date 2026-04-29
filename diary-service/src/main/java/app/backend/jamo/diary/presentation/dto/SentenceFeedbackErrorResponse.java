package app.backend.jamo.diary.presentation.dto;

import java.util.Objects;

/**
 * sentence-feedback API JSON 오류 응답 표준 — code + generic message 만.
 *
 * <p>도메인 예외의 raw message / cause stack 은 클라이언트에 노출 금지 (sanitization 정책 정합 —
 * security-reviewer M-4 패턴 정합).
 */
public record SentenceFeedbackErrorResponse(SentenceFeedbackErrorCode code, String message) {

    public SentenceFeedbackErrorResponse {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
