package app.backend.jamo.chat.presentation.web;

import app.backend.jamo.chat.infrastructure.config.CorsProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Frontend SPA 의 cross-origin 호출(예: {@code POST /api/v1/chat/transcribe}) 허용 CORS 정책.
 * {@code *} 미사용 — 명시 origin(env CORS_ALLOWED_ORIGINS). ObjectProvider no-op (WebMvcTest 슬라이스 대응).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final ObjectProvider<CorsProperties> corsProperties;

    public CorsConfig(ObjectProvider<CorsProperties> corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        CorsProperties props = corsProperties.getIfAvailable();
        if (props == null) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(props.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
