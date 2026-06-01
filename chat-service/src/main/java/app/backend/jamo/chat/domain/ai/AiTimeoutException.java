package app.backend.jamo.chat.domain.ai;

/**
 * ai-service 호출 deadline 초과 — {@link AiUnavailableException} 의 하위 타입(503 매핑 동일).
 *
 * <p>별 타입인 이유: deadline 초과는 "일시 네트워크 장애"가 아니므로 retry 비대상 (code-reviewer H1).
 * Resilience4j retry {@code ignoreExceptions} 로 제외 — deadline×재시도 누적(최악 130s+) 회피.
 * CircuitBreaker 통계에는 여전히 기록 (recordExceptions 가 상위 타입 매칭).
 */
public class AiTimeoutException extends AiUnavailableException {

    public AiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
