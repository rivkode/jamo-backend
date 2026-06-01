package app.backend.jamo.chat.presentation.controller;

import app.backend.jamo.chat.application.dto.SpeakCommand;
import app.backend.jamo.chat.application.dto.SpeakResult;
import app.backend.jamo.chat.application.dto.TranscribeCommand;
import app.backend.jamo.chat.application.service.SpeakTextService;
import app.backend.jamo.chat.application.service.TranscribeAudioService;
import app.backend.jamo.chat.domain.ai.TranscriptResult;
import app.backend.jamo.chat.infrastructure.config.AudioStorageProperties;
import app.backend.jamo.chat.presentation.dto.SpeechRequest;
import app.backend.jamo.chat.presentation.dto.SpeechResponse;
import app.backend.jamo.chat.presentation.dto.TranscribeResponse;
import app.backend.jamo.chat.presentation.web.AuthenticatedUser;
import app.backend.jamo.chat.presentation.web.LoginUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * AI 비즈니스 게이트웨이 — STT/TTS (API_SPEC 부록 E.3, ADR-0003). 모두 🔒 인증.
 *
 * <ul>
 *   <li>POST /api/v1/chat/transcribe — multipart audio → {@code {transcribeInfo:{userId,text}}}</li>
 *   <li>POST /api/v1/chat/speech — {speechText,chatId} → {@code {audioSpeechInfo:{userId,speechText,filePath,audioUrl}}}</li>
 * </ul>
 *
 * <p>실제 추론은 ai-service gRPC (AiSpeechPort). TTS 음성은 chat-service 가 저장 후 {@code /audio/{name}} 서빙.
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerJwt")
public class ChatController {

    private final TranscribeAudioService transcribeService;
    private final SpeakTextService speakService;
    private final AudioStorageProperties storageProperties;

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscribeResponse transcribe(
        @LoginUser AuthenticatedUser auth,
        @RequestParam("audio") MultipartFile audio,
        @RequestParam(value = "chatRoomId", required = false) String chatRoomId
    ) {
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("audio file is required");
        }
        byte[] content;
        try {
            content = audio.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read uploaded audio", e);
        }
        // language=null → ai-service 자동 감지 (한국어/영어 혼용 안전).
        TranscriptResult result = transcribeService.transcribe(
            new TranscribeCommand(auth.userId(), content, extractFormat(audio), null));
        return TranscribeResponse.of(auth.userId(), result.text());
    }

    @PostMapping("/speech")
    public SpeechResponse speech(
        @LoginUser AuthenticatedUser auth,
        @Valid @RequestBody SpeechRequest body
    ) {
        SpeakResult result = speakService.speak(new SpeakCommand(auth.userId(), body.speechText()));
        String filePath = storageProperties.storageDir() + "/" + result.storedName();
        String audioUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/audio/").path(result.storedName()).toUriString();
        return SpeechResponse.of(auth.userId(), body.speechText(), filePath, audioUrl);
    }

    /** 게이트웨이 입력 검증 — 허용 오디오 포맷 화이트리스트 (ADR-0003 게이트웨이 책임, code H2). */
    private static final java.util.Set<String> ALLOWED_FORMATS =
        java.util.Set.of("wav", "mp3", "mpeg", "m4a", "mp4", "aac", "webm", "ogg", "opus", "flac");

    /** 파일명 확장자 → 포맷. 없으면 content-type, 그것도 없으면 wav. 허용 외 포맷은 400. */
    private String extractFormat(MultipartFile audio) {
        String format = "wav";
        String name = audio.getOriginalFilename();
        if (name != null && name.contains(".")) {
            String ext = name.substring(name.lastIndexOf('.') + 1).trim().toLowerCase();
            if (!ext.isBlank()) {
                format = ext;
            }
        } else {
            String contentType = audio.getContentType();
            if (contentType != null && contentType.startsWith("audio/")) {
                format = contentType.substring("audio/".length()).toLowerCase();
            }
        }
        if (!ALLOWED_FORMATS.contains(format)) {
            throw new IllegalArgumentException("unsupported audio format: " + format);
        }
        return format;
    }
}
