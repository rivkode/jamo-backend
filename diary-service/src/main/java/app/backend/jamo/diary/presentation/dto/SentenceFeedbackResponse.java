package app.backend.jamo.diary.presentation.dto;

import app.backend.jamo.diary.application.dto.sentencefeedback.SentenceFeedbackResult;

import java.time.Instant;
import java.util.List;

/**
 * sentence-feedback HTTP 응답 — 박제 §8 SentenceFeedbackResponse 7 필드.
 *
 * <p>request / accept 응답에 사용. reject 는 204 No Content 라 본 record 미사용 (PRD KEEP).
 *
 * <p>UUID 는 String 으로 직렬화 (Jackson default). XSS escape 는 클라 책임 (박제
 * decisions/diary/sentence-feedback-presentation-decisions.md Q3 — JSON spec 정합).
 *
 * @param feedbackId           Aggregate UUID 문자열
 * @param status               REQUESTED / SUGGESTED / ACCEPTED / REJECTED / EXPIRED / FAILED
 * @param originalSentence     사용자 입력 문장 echo
 * @param suggestions          AI 제안 (FAILED 시 chat-service 의 fallback 1건 포함 가능)
 * @param decisionSuggestionId ACCEPTED 시 채택된 suggestionId (UUID 문자열), 그 외 null
 * @param expiresAt            SUGGESTED 시 +24h, final 시 echo, 그 외 null
 * @param processedAt          응답 생성 시점
 */
public record SentenceFeedbackResponse(
    String feedbackId,
    String status,
    String originalSentence,
    List<SuggestionResponse> suggestions,
    String decisionSuggestionId,
    Instant expiresAt,
    Instant processedAt
) {
    public static SentenceFeedbackResponse from(SentenceFeedbackResult result) {
        return new SentenceFeedbackResponse(
            result.feedbackId().toString(),
            result.status(),
            result.originalSentence(),
            result.suggestions().stream().map(SuggestionResponse::from).toList(),
            result.decisionSuggestionId() == null ? null : result.decisionSuggestionId().toString(),
            result.expiresAt(),
            result.processedAt()
        );
    }
}
