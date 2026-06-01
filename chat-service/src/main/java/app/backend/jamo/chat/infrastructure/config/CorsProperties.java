package app.backend.jamo.chat.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Frontend SPA cross-origin 허용 origin (chat-service). 명시 origin 만 (* 미사용). identity/diary 정합.
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
                throw new IllegalArgumentException("CORS origin must start with http:// or https:// — got: " + origin);
            }
            if (origin.endsWith("/")) {
                throw new IllegalArgumentException("CORS origin must not have trailing slash — got: " + origin);
            }
        }
        allowedOrigins = List.copyOf(allowedOrigins);
    }
}
