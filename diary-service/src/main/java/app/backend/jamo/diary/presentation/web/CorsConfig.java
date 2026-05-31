package app.backend.jamo.diary.presentation.web;

import app.backend.jamo.diary.infrastructure.config.CorsProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Frontend SPA 의 cross-origin API 호출(예: {@code GET /api/v1/diaries/feed})을 허용하는 CORS 정책.
 *
 * <p>{@code allowedOrigins} 에 {@code *} 미사용 — 명시 origin (env {@code CORS_ALLOWED_ORIGINS}).
 *
 * <p>{@link CorsProperties} 를 {@link ObjectProvider} 로 받는 이유 — {@code WebMvcConfigurer} 는
 * {@code @WebMvcTest} 슬라이스에 자동 포함되는데, 그 슬라이스엔 {@code CorsProperties} 빈이 없어 생성자
 * 직접 주입 시 모든 컨트롤러 슬라이스가 빈 주입 실패한다. 빈이 없으면(슬라이스) CORS 미적용 no-op,
 * 있으면(전체 앱 / CORS 슬라이스 테스트) 정책 적용.
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
