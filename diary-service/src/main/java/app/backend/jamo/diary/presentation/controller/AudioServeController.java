package app.backend.jamo.diary.presentation.controller;

import app.backend.jamo.diary.application.dto.audio.AudioContent;
import app.backend.jamo.diary.application.service.audio.GetAudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 음성 정적 서빙 — {@code GET /audio/{name}} (무인증, capability URL).
 *
 * <p><b>무인증 이유</b>: 브라우저 {@code <audio src>} 재생은 Authorization 헤더를 실을 수 없다. 대신 파일명이
 * 추측 불가 UUID(`{uuid}.{ext}`)라 이름 자체가 접근 권한(capability)이다. 본문 {@code /api/**} 밖이라
 * CORS 협상도 불필요(미디어 요소 재생은 CORS 비대상).
 *
 * <p>path traversal 은 (1) PathVariable 단일 세그먼트, (2) DB {@code findByStoredName} 미스 시 404,
 * (3) {@code LocalAudioStorage.resolveSafe} 의 3중 방어로 차단.
 */
@RestController
@RequiredArgsConstructor
public class AudioServeController {

    private final GetAudioService getAudioService;

    @GetMapping("/audio/{name}")
    public ResponseEntity<byte[]> serve(@PathVariable String name) {
        AudioContent audio = getAudioService.get(name);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(audio.contentType()))
            // 사용자 업로드 바이너리를 무인증 서빙하므로 XSS/sniffing 방어 (security H1):
            // nosniff = content-type 강제, CSP sandbox + default-src 'none' = 스크립트 실행 차단,
            // Content-Disposition inline = 다운로드 아닌 재생 의도 유지하되 렌더 컨텍스트 격리.
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "default-src 'none'; sandbox; frame-ancestors 'none'")
            .header("Content-Disposition", "inline")
            .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
            .body(audio.content());
    }
}
