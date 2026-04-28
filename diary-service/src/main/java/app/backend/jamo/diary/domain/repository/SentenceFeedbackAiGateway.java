package app.backend.jamo.diary.domain.repository;

import app.backend.jamo.diary.domain.model.sentencefeedback.Suggestion;
import app.backend.jamo.diary.domain.model.sentencefeedback.SentenceText;
import app.backend.jamo.diary.domain.model.sentencefeedback.Tone;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * chat-service 의 AI 비즈니스 게이트웨이 (`AiAssistantService.RequestSentenceFeedback`) 호출 추상화.
 *
 * <p>박제: decisions/diary/sentence-feedback-domain-policy.md §6 (AI 호출 게이트웨이) +
 * decisions/contracts/ai-assistant-service-method-catalog.md §28 (Deadline 35s, status SUGGESTED/FAILED).
 *
 * <p><b>Adapter 책임</b> (D-a-5-impl-infra 시점 구현):
 * <ul>
 *   <li>chat.proto 의 SentenceFeedbackRequest/Response ↔ 본 인터페이스의 Args/Result 변환</li>
 *   <li>gRPC 호출 실패 (Deadline / UNAVAILABLE / 네트워크) → {@link Result.Status#FAILED} +
 *       {@code failureReasonOrNull} 로 일원화 (호출 측 fallback 정책 통합 — validation FAILED 우회 정합)</li>
 *   <li>Tone enum → wire format string 변환 (Application/Mapper layer 책임 — Tone JavaDoc 명시)</li>
 *   <li>userId propagation (chat-service 의 사용자별 quota / 사용량 카운터)</li>
 *   <li>Circuit Breaker / Retry (Resilience4j, ADR-0003)</li>
 * </ul>
 *
 * <p>Application Service 는 본 인터페이스만 의존 — proto 클래스 직접 import 금지 (ArchUnit R3 / R8).
 */
public interface SentenceFeedbackAiGateway {

    /**
     * chat-service 호출. 항상 {@link Result} 반환 (예외 throw 안 함 — Adapter 가 시스템 오류를 FAILED 로 변환).
     */
    Result request(Args args);

    /**
     * 호출 인자.
     *
     * @param userId          호출자 사용자 ID (chat-service rate limit 키)
     * @param sentence        피드백 대상 문장
     * @param priorSentences  앞 문장 컨텍스트 (max 5, §9). 빈 리스트 가능
     * @param toneOrNull      어조 힌트 (null = 미명시 — chat-service default 적용, §10)
     * @param requestId       추적 ID (chat-service 의 분산 trace / 사용량 카운터)
     */
    record Args(
        UUID userId,
        SentenceText sentence,
        List<String> priorSentences,
        Tone toneOrNull,
        String requestId
    ) {
        public Args {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(sentence, "sentence");
            Objects.requireNonNull(priorSentences, "priorSentences");
            Objects.requireNonNull(requestId, "requestId");
            if (priorSentences.size() > 5) {
                throw new IllegalArgumentException("priorSentences max 5, got " + priorSentences.size());
            }
            priorSentences = List.copyOf(priorSentences);
        }
    }

    /**
     * 응답 결과. {@code status=SUGGESTED} 시 {@code suggestions} 1+, {@code status=FAILED} 시
     * {@code failureReasonOrNull} 로 사유 제공 (chat-service 응답의 fallback 1건 또는 시스템 오류).
     */
    record Result(
        Status status,
        List<Suggestion> suggestions,
        String failureReasonOrNull
    ) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(suggestions, "suggestions");
            suggestions = List.copyOf(suggestions);
        }

        public static Result suggested(List<Suggestion> suggestions) {
            if (suggestions.isEmpty()) {
                throw new IllegalArgumentException("SUGGESTED requires at least one suggestion");
            }
            return new Result(Status.SUGGESTED, suggestions, null);
        }

        public static Result failed(String reason) {
            Objects.requireNonNull(reason, "reason");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("failure reason must not be blank");
            }
            return new Result(Status.FAILED, List.of(), reason);
        }

        public enum Status { SUGGESTED, FAILED }
    }
}
