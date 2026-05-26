package app.backend.jamo.diary.presentation.dto;

import java.util.Objects;

/**
 * comment API JSON 오류 응답 표준 — code + generic message 만.
 *
 * <p>{@link DiaryErrorResponse} 패턴 정합 — 도메인 예외의 raw message / cause stack 은 클라이언트에 노출 금지.
 */
public record CommentErrorResponse(CommentErrorCode code, String message) {

    public CommentErrorResponse {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
