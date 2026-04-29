package app.backend.jamo.diary.domain.repository;

import java.util.UUID;

/**
 * 사용자별 문장 피드백 요청 quota port (분당 / 일일).
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §11 (사용자별 일 50회 / 분 10회).
 *
 * <p>구현체는 Infrastructure layer 의 {@code SentenceFeedbackRateLimiterRedisStore} (identity 의
 * {@code ValidationRateLimiter} 패턴 정합 — cooldown 1차 + daily soft cap, TOCTOU race 박제).
 *
 * <p>호출 측 (Application Service):
 * <ol>
 *   <li>{@link #canRequest(UUID)} — false 시 {@code SentenceFeedbackRateLimitedException} (429)</li>
 *   <li>true 면 trans 진입 직전 {@link #recordRequest(UUID)} 호출 (T1 commit 무관 — 한도는 시도 단위)</li>
 * </ol>
 *
 * <p>본 port 는 {@link app.backend.jamo.diary.application.service.sentencefeedback.RequestSentenceFeedbackService}
 * 만 사용 — accept / reject 는 quota 미적용 (사용자가 이미 받은 결정 반영, 박제 §11 의 "호출 한도" 가
 * AI 호출 비용 보호 목적).
 */
public interface SentenceFeedbackRateLimiter {

    boolean canRequest(UUID userId);

    void recordRequest(UUID userId);
}
