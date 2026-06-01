package app.backend.jamo.chat.application.service;

import app.backend.jamo.chat.application.dto.GenerateChatResponseCommand.RecentMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * diarychat AI 자동응답용 프롬프트 합성 — chat-service 게이트웨이의 핵심 책임 (ai-service 는 무상태 추론만).
 *
 * <p>system instruction(한국어 친근 톤 / 1~3문장) + 최근 대화 맥락 + 직전 사용자 메시지를 단일 프롬프트로 조립.
 * 컨텍스트는 최근 {@link #MAX_CONTEXT_MESSAGES} 건으로 제한 (토큰/비용 가드). role 라벨은 한국어 표기.
 */
@Component
public class ChatPromptAssembler {

    /** 컨텍스트 윈도우 상한 — 비용/토큰 가드. */
    static final int MAX_CONTEXT_MESSAGES = 10;

    private static final String SYSTEM_INSTRUCTION =
        "당신은 한국어 3줄 일기 채팅방의 친근한 AI 어시스턴트입니다. "
        + "사용자의 일기와 대화에 공감하며, 1~3문장으로 자연스럽고 따뜻한 한국어로 답하세요. "
        + "민감하거나 개인정보를 캐묻지 말고, 대화를 이어갈 수 있게 가볍게 반응하세요.";

    private static final String LABEL_USER = "사용자";
    private static final String LABEL_ASSISTANT = "AI";

    public String assemble(String userMessage, List<RecentMessage> recentMessages) {
        StringBuilder sb = new StringBuilder(SYSTEM_INSTRUCTION).append("\n\n[대화]\n");

        List<RecentMessage> context = trimContext(recentMessages);
        for (RecentMessage m : context) {
            if (m == null || m.text() == null || m.text().isBlank()) {
                continue;
            }
            sb.append(label(m.role())).append(": ").append(m.text().strip()).append('\n');
        }

        sb.append(LABEL_USER).append(": ").append(userMessage.strip()).append('\n');
        sb.append(LABEL_ASSISTANT).append(": ");
        return sb.toString();
    }

    /** 최근 MAX_CONTEXT_MESSAGES 건만 (뒤에서부터). null/빈 입력 안전. */
    private static List<RecentMessage> trimContext(List<RecentMessage> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return List.of();
        }
        int size = recentMessages.size();
        return size <= MAX_CONTEXT_MESSAGES
            ? recentMessages
            : recentMessages.subList(size - MAX_CONTEXT_MESSAGES, size);
    }

    private static String label(String role) {
        return "assistant".equalsIgnoreCase(role) ? LABEL_ASSISTANT : LABEL_USER;
    }
}
