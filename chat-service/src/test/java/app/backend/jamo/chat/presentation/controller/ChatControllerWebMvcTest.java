package app.backend.jamo.chat.presentation.controller;

import app.backend.jamo.chat.application.service.SpeakTextService;
import app.backend.jamo.chat.application.service.TranscribeAudioService;
import app.backend.jamo.chat.application.dto.SpeakResult;
import app.backend.jamo.chat.domain.ai.AiUnavailableException;
import app.backend.jamo.chat.domain.ai.TranscriptResult;
import app.backend.jamo.chat.domain.audio.AudioStorage;
import app.backend.jamo.chat.infrastructure.config.AudioStorageProperties;
import app.backend.jamo.chat.presentation.exception.ChatExceptionHandler;
import app.backend.jamo.chat.presentation.web.LoginUserArgumentResolver;
import app.backend.jamo.chat.presentation.web.PresentationWebConfig;
import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerificationException;
import app.backend.jamo.common.auth.JwtVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ChatController.class, AudioServeController.class})
@Import({ChatExceptionHandler.class, LoginUserArgumentResolver.class, PresentationWebConfig.class,
    ChatControllerWebMvcTest.TestBeans.class})
class ChatControllerWebMvcTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final String BEARER = "Bearer valid-access";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TranscribeAudioService transcribeService;
    @MockitoBean private SpeakTextService speakService;
    @MockitoBean private AudioStorage audioStorage;
    @MockitoBean private JwtVerifier jwtVerifier;

    private void auth() throws JwtVerificationException {
        when(jwtVerifier.verify("valid-access")).thenReturn(new JwtClaims(
            USER.toString(), "sid-1", "device-1", JwtTokenType.ACCESS, NOW, NOW.plusSeconds(900)));
    }

    @Test
    void transcribe_returns_text() throws Exception {
        auth();
        when(transcribeService.transcribe(any())).thenReturn(new TranscriptResult("안녕하세요", "ko"));
        var file = new MockMultipartFile("audio", "rec.wav", "audio/wav", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/chat/transcribe").file(file).header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transcribeInfo.userId").value(USER.toString()))
            .andExpect(jsonPath("$.transcribeInfo.text").value("안녕하세요"));
    }

    @Test
    void transcribe_empty_audio_400() throws Exception {
        auth();
        var empty = new MockMultipartFile("audio", "rec.wav", "audio/wav", new byte[0]);
        mockMvc.perform(multipart("/api/v1/chat/transcribe").file(empty).header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CHAT_VALIDATION_FAILED"));
    }

    @Test
    void transcribe_no_auth_401() throws Exception {
        var file = new MockMultipartFile("audio", "rec.wav", "audio/wav", new byte[]{1});
        mockMvc.perform(multipart("/api/v1/chat/transcribe").file(file))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void speech_returns_filePath_and_audioUrl() throws Exception {
        auth();
        when(speakService.speak(any())).thenReturn(new SpeakResult("abc.mp3"));

        mockMvc.perform(post("/api/v1/chat/speech").header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"speechText\":\"읽어줘\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.audioSpeechInfo.userId").value(USER.toString()))
            .andExpect(jsonPath("$.audioSpeechInfo.speechText").value("읽어줘"))
            .andExpect(jsonPath("$.audioSpeechInfo.filePath").value("/app/audio-storage/abc.mp3"))
            .andExpect(jsonPath("$.audioSpeechInfo.audioUrl").value(org.hamcrest.Matchers.endsWith("/audio/abc.mp3")));
    }

    @Test
    void speech_blank_400() throws Exception {
        auth();
        mockMvc.perform(post("/api/v1/chat/speech").header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"speechText\":\"  \"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void speech_over_4096_chars_400() throws Exception {
        auth();
        String tooLong = "가".repeat(4097);
        mockMvc.perform(post("/api/v1/chat/speech").header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"speechText\":\"" + tooLong + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CHAT_VALIDATION_FAILED"));
    }

    @Test
    void transcribe_unsupported_format_400() throws Exception {
        auth();
        // 허용 외 확장자 → 게이트웨이 화이트리스트 거부 (code H2).
        var file = new MockMultipartFile("audio", "malware.exe", "application/octet-stream", new byte[]{1, 2});
        mockMvc.perform(multipart("/api/v1/chat/transcribe").file(file).header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CHAT_VALIDATION_FAILED"));
    }

    @Test
    void speech_ai_unavailable_503() throws Exception {
        auth();
        when(speakService.speak(any())).thenThrow(new AiUnavailableException("circuit open"));
        mockMvc.perform(post("/api/v1/chat/speech").header(HttpHeaders.AUTHORIZATION, BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"speechText\":\"읽어줘\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
    }

    @Test
    void serve_returns_audio_with_security_headers() throws Exception {
        when(audioStorage.load("abc.mp3")).thenReturn(Optional.of(new byte[]{9, 8, 7}));
        mockMvc.perform(get("/audio/{name}", "abc.mp3"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/mpeg"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void serve_not_found_404() throws Exception {
        when(audioStorage.load("missing.mp3")).thenReturn(Optional.empty());
        mockMvc.perform(get("/audio/{name}", "missing.mp3"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("AUDIO_NOT_FOUND"));
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        AudioStorageProperties audioStorageProperties() {
            return new AudioStorageProperties("/app/audio-storage");
        }
    }
}
