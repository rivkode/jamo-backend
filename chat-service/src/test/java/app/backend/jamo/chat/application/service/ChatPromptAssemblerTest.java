package app.backend.jamo.chat.application.service;

import app.backend.jamo.chat.application.dto.GenerateChatResponseCommand.RecentMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPromptAssemblerTest {

    private final ChatPromptAssembler assembler = new ChatPromptAssembler();

    @Test
    void includes_user_message_and_assistant_cue() {
        String prompt = assembler.assemble("오늘 기분 좋아", List.of());
        assertThat(prompt).contains("사용자: 오늘 기분 좋아");
        assertThat(prompt).endsWith("AI: ");
    }

    @Test
    void maps_roles_to_korean_labels_in_order() {
        String prompt = assembler.assemble("지금 메시지", List.of(
            new RecentMessage("user", "이전 사용자"),
            new RecentMessage("assistant", "이전 AI")));
        int userIdx = prompt.indexOf("사용자: 이전 사용자");
        int aiIdx = prompt.indexOf("AI: 이전 AI");
        assertThat(userIdx).isGreaterThanOrEqualTo(0);
        assertThat(aiIdx).isGreaterThan(userIdx);
    }

    @Test
    void trims_context_to_max_messages() {
        List<RecentMessage> many = new ArrayList<>();
        for (int i = 0; i < ChatPromptAssembler.MAX_CONTEXT_MESSAGES + 5; i++) {
            many.add(new RecentMessage("user", "msg" + i));
        }
        String prompt = assembler.assemble("현재", many);
        // 가장 오래된 5건은 잘려 빠져야 함.
        assertThat(prompt).doesNotContain("msg0");
        assertThat(prompt).doesNotContain("msg4");
        assertThat(prompt).contains("msg5");
        assertThat(prompt).contains("msg" + (ChatPromptAssembler.MAX_CONTEXT_MESSAGES + 4));
    }

    @Test
    void skips_blank_context_messages() {
        String prompt = assembler.assemble("현재", List.of(
            new RecentMessage("user", "  "),
            new RecentMessage("user", null)));
        // blank/null 컨텍스트는 라벨 없이 건너뜀 — 현재 메시지/큐만 남음.
        assertThat(prompt).contains("사용자: 현재");
    }

    @Test
    void null_context_is_safe() {
        String prompt = assembler.assemble("현재", null);
        assertThat(prompt).contains("사용자: 현재");
    }
}
