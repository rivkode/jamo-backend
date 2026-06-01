package app.backend.jamo.chat.application.dto;

import java.util.List;

/**
 * diarychat AI 자동응답 생성 명령 (AiAssistantService.GenerateChatResponse, S4).
 *
 * @param userId         호출자(메시지 작성자) ID — rate limit 키
 * @param roomId         diarychat 방 ID (추적/로그용)
 * @param userMessage    사용자가 방에 보낸 직전 메시지 (응답 대상)
 * @param recentMessages 같은 방 최근 메시지 (시간 오래된 순) — 컨텍스트 윈도우
 */
public record GenerateChatResponseCommand(
    String userId,
    String roomId,
    String userMessage,
    List<RecentMessage> recentMessages
) {
    /** 컨텍스트 메시지 한 건 — role 은 "user"/"assistant"/"system" (chat.proto author_role 정합). */
    public record RecentMessage(String role, String text) {
    }
}
