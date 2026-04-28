package app.backend.jamo.diary.application.dto.sentencefeedback;

import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceFeedback;
import app.backend.jamo.diary.domain.model.sentencefeedback.SuggestionId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application layer 응답 — request / accept / reject use case 의 공통 반환.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §8 SentenceFeedbackResponse 7 필드.
 *
 * <p>{@code reject} 는 PRD `accept §2` 박제대로 200 + 본 record (status=REJECTED) 반환 가능 — Presentation
 * 슬라이스에서 204 No Content 로 변환 (HTTP 응답 코드 차이는 Controller 책임).
 *
 * @param feedbackId           Aggregate ID
 * @param status               REQUESTED / SUGGESTED / ACCEPTED / REJECTED / EXPIRED / FAILED
 * @param originalSentence     사용자 입력 문장 echo
 * @param suggestions          AI 제안 (FAILED 시 chat-service 의 fallback 1건 포함 가능, EXPIRED/REJECTED 시 SUGGESTED 시점 그대로 보존)
 * @param decisionSuggestionId ACCEPTED 시 채택된 suggestionId, 그 외 null
 * @param expiresAt            SUGGESTED 시 +24h, final 시 echo
 * @param processedAt          응답 생성 시점
 */
public record SentenceFeedbackResult(
    UUID feedbackId,
    String status,
    String originalSentence,
    List<SuggestionView> suggestions,
    UUID decisionSuggestionId,
    Instant expiresAt,
    Instant processedAt
) {
    public SentenceFeedbackResult {
        suggestions = List.copyOf(suggestions);
    }

    /**
     * 도메인 Aggregate → Application Result 변환.
     *
     * @param processedAt 응답 생성 시점 (Application Service 의 Clock 으로 결정)
     */
    public static SentenceFeedbackResult from(SentenceFeedback feedback, Instant processedAt) {
        List<SuggestionView> views = feedback.suggestions().stream()
            .map(SuggestionView::from)
            .toList();
        return new SentenceFeedbackResult(
            feedback.id().value(),
            feedback.status().name(),
            feedback.originalSentence().value(),
            views,
            feedback.decisionSuggestionId().map(SuggestionId::value).orElse(null),
            feedback.expiresAt().orElse(null),
            processedAt
        );
    }
}
