package app.backend.jamo.chat.application.service;

import app.backend.jamo.chat.application.dto.GenerateChatResponseCommand;
import app.backend.jamo.chat.application.dto.GenerateChatResponseResult;
import app.backend.jamo.chat.domain.ai.AiCompletionPort;
import app.backend.jamo.chat.domain.ai.AiRateLimiter;
import app.backend.jamo.chat.domain.ai.AiUnavailableException;
import app.backend.jamo.chat.domain.ai.LlmCompletion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * diarychat AI 자동응답 생성 (AiAssistantService.GenerateChatResponse, S4).
 *
 * <p>흐름: rate limit 가드 → 프롬프트 합성 → ai-service Complete(LLM) → 사용 가능 응답이면 OK,
 * 부적합/장애는 FAILED, 한도 초과는 RATE_LIMITED. 예외를 호출 측(gRPC server)으로 던지지 않고
 * status 로 일원화 — diary-service 가 SYSTEM 안내 메시지로 단순 매핑 (DiaryQueryGrpcService body-status 정합).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenerateChatResponseService {

    // LLM 파라미터 — 친근한 자연 응답(temperature 0.7), 1~3문장 가드(maxTokens 256).
    private static final double TEMPERATURE = 0.7;
    private static final int MAX_TOKENS = 256;

    private final AiRateLimiter rateLimiter;
    private final ChatPromptAssembler promptAssembler;
    private final AiCompletionPort completionPort;

    public GenerateChatResponseResult generate(GenerateChatResponseCommand command) {
        if (!rateLimiter.tryAcquire(command.userId())) {
            log.info("diarychat AI rate limited: userId={} roomId={}", command.userId(), command.roomId());
            return GenerateChatResponseResult.rateLimited();
        }

        String prompt = promptAssembler.assemble(command.userMessage(), command.recentMessages());
        try {
            LlmCompletion completion = completionPort.complete(prompt, TEMPERATURE, MAX_TOKENS);
            if (!completion.isUsable()) {
                log.warn("diarychat AI unusable completion: roomId={} finishReason={}",
                    command.roomId(), completion.finishReason());
                return GenerateChatResponseResult.failed();
            }
            return GenerateChatResponseResult.ok(completion.text().strip());
        } catch (AiUnavailableException e) {
            log.warn("diarychat AI generation failed: roomId={} cause={}",
                command.roomId(), e.getClass().getSimpleName());
            return GenerateChatResponseResult.failed();
        }
    }
}
