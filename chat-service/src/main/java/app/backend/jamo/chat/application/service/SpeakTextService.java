package app.backend.jamo.chat.application.service;

import app.backend.jamo.chat.application.dto.SpeakCommand;
import app.backend.jamo.chat.application.dto.SpeakResult;
import app.backend.jamo.chat.domain.ai.AiSpeechPort;
import app.backend.jamo.chat.domain.ai.SynthesizedSpeech;
import app.backend.jamo.chat.domain.audio.AudioStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * POST /api/v1/chat/speech — 텍스트 → 음성 (ai-service TTS 게이트웨이) + 합성 음성 저장.
 * filePath 는 presentation 이 storedName 으로 조립 ({@code /audio/{name}} 정적 서빙).
 */
@Service
@RequiredArgsConstructor
public class SpeakTextService {

    private static final String DEFAULT_EXT = "mp3";

    private final AiSpeechPort aiSpeechPort;
    private final AudioStorage audioStorage;

    public SpeakResult speak(SpeakCommand command) {
        SynthesizedSpeech speech = aiSpeechPort.synthesize(command.text(), null, 0.0, null);
        String ext = (speech.audioFormat() == null || speech.audioFormat().isBlank())
            ? DEFAULT_EXT
            : speech.audioFormat();
        String storedName = UUID.randomUUID() + "." + ext;
        audioStorage.store(storedName, speech.audio());
        return new SpeakResult(storedName);
    }
}
