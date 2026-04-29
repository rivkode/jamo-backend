package app.backend.jamo.diary.presentation.dto;

import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/diaries/sentence-feedback/{feedbackId}/reject Request body.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §15 (자유 텍스트 / null 허용 / 1000cp).
 *
 * <p>Bean Validation 1차 4000 char 거부 — 도메인 Aggregate {@code reject(reasonOrNull)} 가 1000 code
 * points 정확 검증 (이모지 정합).
 *
 * @param reason 거부 이유 (선택, blank 또는 null 허용 — 도메인이 정규화)
 */
public record RejectSentenceFeedbackRequest(
    @Size(max = 4000, message = "reason too long")
    String reason
) {
}
