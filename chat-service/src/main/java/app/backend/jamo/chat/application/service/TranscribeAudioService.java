package app.backend.jamo.chat.application.service;

import app.backend.jamo.chat.application.dto.TranscribeCommand;
import app.backend.jamo.chat.domain.ai.AiSpeechPort;
import app.backend.jamo.chat.domain.ai.TranscriptResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * POST /api/v1/chat/transcribe — 음성 → 텍스트 (ai-service STT 게이트웨이, ADR-0003).
 * 본 게이트웨이는 stateless (DB 없음). rate limit / quota 는 후속.
 */
@Service
@RequiredArgsConstructor
public class TranscribeAudioService {

    private final AiSpeechPort aiSpeechPort;

    public TranscriptResult transcribe(TranscribeCommand command) {
        return aiSpeechPort.transcribe(command.audio(), command.format(), command.language());
    }
}
