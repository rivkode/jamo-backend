package app.backend.jamo.chat.application.dto;

/**
 * AI 자동응답 생성 결과 (ChatResponseReply 매핑 소스).
 *
 * <p>status 카탈로그: OK(정상) / FAILED(AI 장애·부적합 응답) / RATE_LIMITED(사용량 한도). diary-service 는
 * OK 면 AI 메시지, 그 외엔 SYSTEM 안내 메시지를 채팅방에 남긴다 (S4 결정).
 */
public record GenerateChatResponseResult(String assistantMessage, Status status) {

    public enum Status {
        OK,
        FAILED,
        RATE_LIMITED
    }

    public static GenerateChatResponseResult ok(String assistantMessage) {
        return new GenerateChatResponseResult(assistantMessage, Status.OK);
    }

    public static GenerateChatResponseResult failed() {
        return new GenerateChatResponseResult("", Status.FAILED);
    }

    public static GenerateChatResponseResult rateLimited() {
        return new GenerateChatResponseResult("", Status.RATE_LIMITED);
    }
}
