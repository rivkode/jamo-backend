package app.backend.jamo.chat.presentation.controller;

import app.backend.jamo.chat.domain.audio.AudioStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * TTS 합성 음성 정적 서빙 — {@code GET /audio/{name}} (무인증 capability URL). diary-service S1 정합.
 *
 * <p>무인증 이유: {@code <audio src>} 재생이 Authorization 헤더 못 실음. 파일명이 추측 불가 UUID 라 이름 자체가
 * 접근 권한. 사용자 업로드가 아닌 <b>서버 생성 TTS(mp3)</b> 라 위장 위험 낮으나 nosniff/CSP 동일 적용.
 * 본문 {@code /api/**} 밖이라 CORS 비대상(미디어 재생).
 */
@RestController
@RequiredArgsConstructor
public class AudioServeController {

    private final AudioStorage audioStorage;

    @GetMapping("/audio/{name}")
    public ResponseEntity<byte[]> serve(@PathVariable String name) {
        byte[] content = audioStorage.load(name)
            .orElseThrow(() -> new AudioNotFoundException("audio not found: " + name));
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentTypeOf(name)))
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "default-src 'none'; sandbox; frame-ancestors 'none'")
            .header("Content-Disposition", "inline")
            .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
            .body(content);
    }

    private String contentTypeOf(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lower.endsWith(".ogg") || lower.endsWith(".opus")) {
            return "audio/ogg";
        }
        return "audio/mpeg";  // mp3 (TTS 기본)
    }

    /** 저장 음성 부재 → 404 (ChatExceptionHandler 매핑). */
    public static class AudioNotFoundException extends RuntimeException {
        public AudioNotFoundException(String message) {
            super(message);
        }
    }
}
