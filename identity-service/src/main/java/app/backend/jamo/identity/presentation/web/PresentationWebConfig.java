package app.backend.jamo.identity.presentation.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * identity-service presentation 의 Spring MVC 설정.
 * 본 모듈 한정 ArgumentResolver 등록 — {@link LoginUserArgumentResolver}.
 * CORS 정책은 별도 {@link CorsConfig} 가 담당 (WebMvcTest 슬라이스 영향 격리).
 */
@Configuration
public class PresentationWebConfig implements WebMvcConfigurer {

    private final LoginUserArgumentResolver loginUserArgumentResolver;

    public PresentationWebConfig(LoginUserArgumentResolver loginUserArgumentResolver) {
        this.loginUserArgumentResolver = loginUserArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }
}
