package app.backend.jamo.chat.application.service;

import app.backend.jamo.chat.application.dto.SpeakCommand;
import app.backend.jamo.chat.application.dto.SpeakResult;
import app.backend.jamo.chat.domain.ai.AiSpeechPort;
import app.backend.jamo.chat.domain.ai.SynthesizedSpeech;
import app.backend.jamo.chat.domain.audio.AudioStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeakTextServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private AiSpeechPort aiSpeechPort;
    private AudioStorage audioStorage;
    private SpeakTextService service;

    @BeforeEach
    void setUp() {
        aiSpeechPort = mock(AiSpeechPort.class);
        audioStorage = mock(AudioStorage.class);
        service = new SpeakTextService(aiSpeechPort, audioStorage);
    }

    @Test
    void synthesizes_and_stores_with_mp3_extension() {
        when(aiSpeechPort.synthesize(eq("안녕"), any(), eq(0.0), any()))
            .thenReturn(new SynthesizedSpeech("AUDIO".getBytes(), "mp3"));

        SpeakResult result = service.speak(new SpeakCommand(USER, "안녕"));

        assertThat(result.storedName()).endsWith(".mp3");
        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(audioStorage).store(name.capture(), bytes.capture());
        assertThat(name.getValue()).isEqualTo(result.storedName());
        assertThat(bytes.getValue()).isEqualTo("AUDIO".getBytes());
    }

    @Test
    void blank_format_falls_back_to_mp3() {
        when(aiSpeechPort.synthesize(any(), any(), eq(0.0), any()))
            .thenReturn(new SynthesizedSpeech(new byte[]{1}, ""));

        SpeakResult result = service.speak(new SpeakCommand(USER, "t"));

        assertThat(result.storedName()).endsWith(".mp3");
    }
}
