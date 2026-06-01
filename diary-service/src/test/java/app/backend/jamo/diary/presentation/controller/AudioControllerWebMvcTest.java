package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.common.auth.JwtClaims;
import app.backend.jamo.common.auth.JwtTokenType;
import app.backend.jamo.common.auth.JwtVerificationException;
import app.backend.jamo.common.auth.JwtVerifier;
import app.backend.jamo.diary.application.dto.audio.AudioContent;
import app.backend.jamo.diary.application.dto.audio.AudioUploadResult;
import app.backend.jamo.diary.application.service.audio.GetAudioService;
import app.backend.jamo.diary.application.service.audio.UploadAudioService;
import app.backend.jamo.diary.domain.exception.AudioClipNotFoundException;
import app.backend.jamo.diary.infrastructure.config.AudioStorageProperties;
import app.backend.jamo.diary.presentation.exception.AudioExceptionHandler;
import app.backend.jamo.diary.presentation.web.LoginUserArgumentResolver;
import app.backend.jamo.diary.presentation.web.PresentationWebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AudioController.class, AudioServeController.class})
@Import({AudioExceptionHandler.class, LoginUserArgumentResolver.class, PresentationWebConfig.class,
    AudioControllerWebMvcTest.Beans.class})
class AudioControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SID = "sid-1";
    private static final String DEVICE_ID = "device-1";
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final String BEARER = "Bearer valid-access";
    private static final String STORED = "abcd.wav";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UploadAudioService uploadAudioService;
    @MockitoBean private GetAudioService getAudioService;
    @MockitoBean private JwtVerifier jwtVerifier;

    private void mockValidAuth() throws JwtVerificationException {
        when(jwtVerifier.verify("valid-access")).thenReturn(new JwtClaims(
            USER_ID.toString(), SID, DEVICE_ID, JwtTokenType.ACCESS, NOW, NOW.plusSeconds(900)));
    }

    @Test
    void upload_returns_201_with_audioUrl_and_filePath() throws Exception {
        mockValidAuth();
        when(uploadAudioService.upload(any()))
            .thenReturn(new AudioUploadResult(STORED, "audio/wav", 3));

        var file = new MockMultipartFile("audio", "rec.wav", "audio/wav", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/audio").file(file)
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fileName").value(STORED))
            .andExpect(jsonPath("$.audioUrl").value(org.hamcrest.Matchers.endsWith("/audio/" + STORED)))
            .andExpect(jsonPath("$.filePath").value("/app/audio-storage/" + STORED))
            .andExpect(jsonPath("$.contentType").value("audio/wav"))
            .andExpect(jsonPath("$.sizeBytes").value(3));
    }

    @Test
    void upload_without_auth_returns_401() throws Exception {
        var file = new MockMultipartFile("audio", "rec.wav", "audio/wav", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/v1/audio").file(file))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void upload_empty_file_returns_400() throws Exception {
        mockValidAuth();
        var empty = new MockMultipartFile("audio", "rec.wav", "audio/wav", new byte[0]);
        mockMvc.perform(multipart("/api/v1/audio").file(empty)
                .header(HttpHeaders.AUTHORIZATION, BEARER))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("AUDIO_VALIDATION_FAILED"));
    }

    @Test
    void serve_returns_audio_bytes_200_with_security_headers() throws Exception {
        // capability URL: storedName 만으로 무인증·owner 무관 200 (의도된 보안 설계).
        // 무인증 서빙이라 nosniff + CSP sandbox 로 위장 콘텐츠 XSS 차단 (security H1).
        when(getAudioService.get(STORED)).thenReturn(new AudioContent(new byte[]{9, 8, 7}, "audio/webm"));

        mockMvc.perform(get("/audio/{name}", STORED))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/webm"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Content-Security-Policy",
                org.hamcrest.Matchers.containsString("sandbox")))
            .andExpect(content().bytes(new byte[]{9, 8, 7}));
    }

    @Test
    void serve_not_found_returns_404() throws Exception {
        when(getAudioService.get("missing.wav"))
            .thenThrow(new AudioClipNotFoundException("audio not found: missing.wav"));

        mockMvc.perform(get("/audio/{name}", "missing.wav"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("AUDIO_NOT_FOUND"));
    }

    @TestConfiguration
    static class Beans {
        @Bean
        AudioStorageProperties audioStorageProperties() {
            return new AudioStorageProperties("/app/audio-storage");
        }
    }
}
