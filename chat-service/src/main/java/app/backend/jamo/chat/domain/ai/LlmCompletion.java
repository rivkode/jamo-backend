package app.backend.jamo.chat.domain.ai;

/**
 * LLM 텍스트 생성 결과 (ai-service AiService.Complete 응답의 도메인 표현).
 *
 * <p>{@code finishReason} 은 공급사 다양성 흡수를 위해 string (ai.proto 정합) — "stop" / "length" /
 * "content_filter" / "error" 등. {@link #isUsable()} 로 채팅 응답으로 쓸 수 있는지 판단.
 */
public record LlmCompletion(String text, String finishReason) {

    public LlmCompletion {
        if (text == null) {
            text = "";
        }
        if (finishReason == null) {
            finishReason = "";
        }
    }

    /** content_filter / error / 빈 텍스트는 채팅 응답으로 부적합 — 호출 측이 FAILED 처리. */
    public boolean isUsable() {
        return !text.isBlank()
            && !"error".equalsIgnoreCase(finishReason)
            && !"content_filter".equalsIgnoreCase(finishReason);
    }
}
