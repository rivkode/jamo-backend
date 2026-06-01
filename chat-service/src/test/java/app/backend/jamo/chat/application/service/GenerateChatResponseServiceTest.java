package app.backend.jamo.chat.application.service;

import app.backend.jamo.chat.application.dto.GenerateChatResponseCommand;
import app.backend.jamo.chat.application.dto.GenerateChatResponseResult;
import app.backend.jamo.chat.domain.ai.AiCompletionPort;
import app.backend.jamo.chat.domain.ai.AiRateLimiter;
import app.backend.jamo.chat.domain.ai.AiUnavailableException;
import app.backend.jamo.chat.domain.ai.LlmCompletion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerateChatResponseServiceTest {

    private static final String USER = "user-1";

    private AiRateLimiter rateLimiter;
    private AiCompletionPort completionPort;
    private GenerateChatResponseService service;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(AiRateLimiter.class);
        completionPort = mock(AiCompletionPort.class);
        service = new GenerateChatResponseService(rateLimiter, new ChatPromptAssembler(), completionPort);
    }

    private GenerateChatResponseCommand cmd() {
        return new GenerateChatResponseCommand(USER, "1", "오늘 어땠어?", List.of());
    }

    @Test
    void rate_limited_returns_RATE_LIMITED_without_calling_llm() {
        when(rateLimiter.tryAcquire(USER)).thenReturn(false);

        GenerateChatResponseResult result = service.generate(cmd());

        assertThat(result.status()).isEqualTo(GenerateChatResponseResult.Status.RATE_LIMITED);
        // proto string 필드는 null 미허용 — 실패 결과도 빈 문자열이어야 setAssistantMessage NPE 회피 (test-reviewer M3).
        assertThat(result.assistantMessage()).isEmpty();
        verify(completionPort, never()).complete(any(), anyDouble(), anyInt());
    }

    @Test
    void usable_completion_returns_OK_with_trimmed_text() {
        when(rateLimiter.tryAcquire(USER)).thenReturn(true);
        when(completionPort.complete(any(), anyDouble(), anyInt()))
            .thenReturn(new LlmCompletion("  좋은 하루였네요!  ", "stop"));

        GenerateChatResponseResult result = service.generate(cmd());

        assertThat(result.status()).isEqualTo(GenerateChatResponseResult.Status.OK);
        assertThat(result.assistantMessage()).isEqualTo("좋은 하루였네요!");
    }

    @Test
    void blank_completion_returns_FAILED() {
        when(rateLimiter.tryAcquire(USER)).thenReturn(true);
        when(completionPort.complete(any(), anyDouble(), anyInt()))
            .thenReturn(new LlmCompletion("   ", "stop"));

        assertThat(service.generate(cmd()).status()).isEqualTo(GenerateChatResponseResult.Status.FAILED);
    }

    @Test
    void content_filtered_completion_returns_FAILED() {
        when(rateLimiter.tryAcquire(USER)).thenReturn(true);
        when(completionPort.complete(any(), anyDouble(), anyInt()))
            .thenReturn(new LlmCompletion("부적절", "content_filter"));

        assertThat(service.generate(cmd()).status()).isEqualTo(GenerateChatResponseResult.Status.FAILED);
    }

    @Test
    void ai_unavailable_returns_FAILED() {
        when(rateLimiter.tryAcquire(USER)).thenReturn(true);
        when(completionPort.complete(any(), anyDouble(), anyInt()))
            .thenThrow(new AiUnavailableException("circuit open"));

        assertThat(service.generate(cmd()).status()).isEqualTo(GenerateChatResponseResult.Status.FAILED);
    }

    @Test
    void llm_called_with_configured_params() {
        when(rateLimiter.tryAcquire(USER)).thenReturn(true);
        when(completionPort.complete(any(), eq(0.7), eq(256)))
            .thenReturn(new LlmCompletion("ok", "stop"));

        assertThat(service.generate(cmd()).status()).isEqualTo(GenerateChatResponseResult.Status.OK);
        verify(completionPort).complete(any(), eq(0.7), eq(256));
    }
}
