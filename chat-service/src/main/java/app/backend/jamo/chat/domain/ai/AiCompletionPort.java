package app.backend.jamo.chat.domain.ai;

/**
 * ai-service AiService.Complete (LLM 텍스트 생성) 호출 추상화 port (ADR-0003 — chat-service 만 ai-service 호출).
 *
 * <p>구현은 infrastructure 의 gRPC 어댑터 (Deadline + Resilience4j CB/Retry/fallback). 입력 프롬프트는
 * chat-service 가 템플릿/컨텍스트 합성을 마친 최종 문자열.
 */
public interface AiCompletionPort {

    /** 프롬프트 → LLM 응답. 실패 시 {@link AiUnavailableException}. */
    LlmCompletion complete(String prompt, double temperature, int maxTokens);
}
