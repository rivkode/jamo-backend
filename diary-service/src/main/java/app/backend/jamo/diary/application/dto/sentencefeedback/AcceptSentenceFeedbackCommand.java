package app.backend.jamo.diary.application.dto.sentencefeedback;

import java.util.Objects;
import java.util.UUID;

/**
 * 제안 채택 use case 입력.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §4 (404 IDOR — feedbackId 가 다른 사용자
 * 소유면 404) / §1 (suggestionId UUID).
 *
 * @param userId       호출자 사용자 ID (소유자 검증)
 * @param feedbackId   대상 SentenceFeedback ID
 * @param suggestionId 채택할 제안 ID (Aggregate 의 suggestions 안에 존재해야 함)
 */
public record AcceptSentenceFeedbackCommand(
    UUID userId,
    UUID feedbackId,
    UUID suggestionId
) {
    public AcceptSentenceFeedbackCommand {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(feedbackId, "feedbackId");
        Objects.requireNonNull(suggestionId, "suggestionId");
    }
}
