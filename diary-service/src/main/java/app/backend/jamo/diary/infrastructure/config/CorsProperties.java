package app.backend.jamo.diary.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Frontend SPA 의 cross-origin 호출(예: {@code GET /api/v1/diaries/feed})을 허용하기 위한 CORS 정책.
 *
 * <p>{@code *} 대신 <b>명시 origin</b> 만 허용한다. dev/local 은 {@code http://localhost:3000}, prod 는
 * {@code https://jamoai.app} — env {@code CORS_ALLOWED_ORIGINS} (쉼표 구분) 로 override.
 *
 * <p>compact constructor 가 fail-fast 검증:
 * <ul>
 *   <li>null/empty 목록 거부 (CORS 미설정 시 프론트가 전면 차단됨 — 조용한 누락 방지)</li>
 *   <li>각 origin 이 http:// 또는 https:// 로 시작 + path/trailing slash 없음 (origin 은 scheme+host+port)</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "jamo.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("jamo.cors.allowed-origins must not be empty");
        }
        for (String origin : allowedOrigins) {
            if (origin == null || origin.isBlank()) {
                throw new IllegalArgumentException("jamo.cors.allowed-origins must not contain blank entry");
            }
            if (!origin.startsWith("http://") && !origin.startsWith("https://")) {
                throw new IllegalArgumentException(
                        "CORS origin must start with http:// or https:// — got: " + origin);
            }
            if (origin.endsWith("/")) {
                throw new IllegalArgumentException(
                        "CORS origin must not have trailing slash (scheme+host+port only) — got: " + origin);
            }
        }
        allowedOrigins = List.copyOf(allowedOrigins);
    }
}
