package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.diary.application.dto.audio.AudioUploadResult;
import app.backend.jamo.diary.application.dto.audio.UploadAudioCommand;
import app.backend.jamo.diary.application.service.audio.UploadAudioService;
import app.backend.jamo.diary.domain.exception.InvalidAudioException;
import app.backend.jamo.diary.infrastructure.config.AudioStorageProperties;
import app.backend.jamo.diary.presentation.dto.UploadAudioResponse;
import app.backend.jamo.diary.presentation.web.AuthenticatedUser;
import app.backend.jamo.diary.presentation.web.LoginUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 음성 업로드 API (API_SPEC 부록 E — 녹음→저장 MVP). 🔒 인증 필요.
 *
 * <p>{@code POST /api/v1/audio} (multipart {@code audio}) — 녹음 파일 업로드 → 저장 후 재생 URL 반환.
 * 재생은 무인증 정적 서빙 {@code GET /audio/{name}} ({@link AudioServeController}) — 파일명이 추측 불가
 * UUID 라 capability URL 역할.
 */
@RestController
@RequestMapping("/api/v1/audio")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerJwt")
public class AudioController {

    private final UploadAudioService uploadAudioService;
    private final AudioStorageProperties storageProperties;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadAudioResponse> upload(
        @LoginUser AuthenticatedUser auth,
        @RequestParam("audio") MultipartFile audio
    ) {
        if (audio == null || audio.isEmpty()) {
            throw new InvalidAudioException("audio file is required");
        }
        byte[] content;
        try {
            content = audio.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read uploaded audio", e);
        }

        AudioUploadResult result = uploadAudioService.upload(
            new UploadAudioCommand(auth.userId(), content, audio.getContentType()));

        String audioUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/audio/").path(result.storedName()).toUriString();
        String filePath = storageProperties.storageDir() + "/" + result.storedName();

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(UploadAudioResponse.of(result, audioUrl, filePath));
    }
}
