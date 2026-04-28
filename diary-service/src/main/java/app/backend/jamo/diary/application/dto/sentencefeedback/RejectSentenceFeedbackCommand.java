package app.backend.jamo.diary.application.dto.sentencefeedback;

import java.util.Objects;
import java.util.UUID;

/**
 * 제안 거부 use case 입력.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §4 (404 IDOR) / §15 (reason 자유 텍스트, null/blank 허용,
 * Aggregate 가 1000 cp 상한 검증).
 *
 * @param userId       호출자 사용자 ID (소유자 검증)
 * @param feedbackId   대상 SentenceFeedback ID
 * @param reasonOrNull 거부 사유 (자유 텍스트, null/blank 허용 — Aggregate 가 null 정규화)
 */
public record RejectSentenceFeedbackCommand(
    UUID userId,
    UUID feedbackId,
    String reasonOrNull
) {
    public RejectSentenceFeedbackCommand {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(feedbackId, "feedbackId");
        // reasonOrNull 은 nullable
    }
}
