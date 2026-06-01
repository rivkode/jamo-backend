package app.backend.jamo.diary.domain.repository;

import java.util.List;
import java.util.UUID;

/**
 * diarychat AI 자동응답 게이트웨이 port — chat-service AiAssistantService.GenerateChatResponse 호출 추상화 (S4).
 *
 * <p>ADR-0003: diary-service 는 ai-service 를 직접 호출하지 않고 chat-service 게이트웨이만 호출. 구현은
 * infrastructure 의 gRPC 어댑터 (Deadline + Resilience4j CB/Retry/fallback). 실패는 예외가 아닌
 * {@link Status#FAILED} 로 일원화 — 호출 측(AiAutoResponder)이 SYSTEM 안내 메시지로 매핑.
 */
public interface DiaryChatAiGateway {

    Result generate(Args args);

    /**
     * @param userId         메시지 작성자 (chat-service rate limit 키)
     * @param roomId         방 ID (추적용)
     * @param userMessage    응답 대상 사용자 메시지
     * @param recentMessages 컨텍스트 (시간 오래된 순)
     * @param requestId      분산 trace ID
     */
    record Args(UUID userId, long roomId, String userMessage,
                List<RecentMessage> recentMessages, String requestId) {
    }

    /** 컨텍스트 메시지 — role 은 "user"/"assistant"/"system" (chat.proto author_role 정합). */
    record RecentMessage(String role, String text) {
    }

    /** 생성 결과 — status 에 따라 호출 측이 AI / SYSTEM 메시지 분기. */
    record Result(Status status, String assistantMessage) {

        public static Result ok(String assistantMessage) {
            return new Result(Status.OK, assistantMessage);
        }

        public static Result failed() {
            return new Result(Status.FAILED, "");
        }

        public static Result rateLimited() {
            return new Result(Status.RATE_LIMITED, "");
        }
    }

    enum Status {
        OK,
        FAILED,
        RATE_LIMITED
    }
}
