package app.backend.jamo.diary.presentation.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * diary-service presentation 의 Spring MVC 설정. {@link LoginUserArgumentResolver} 등록.
 * CORS 정책은 별도 {@link CorsConfig} 가 담당 (WebMvcTest 슬라이스 영향 격리).
 */
@Configuration
@RequiredArgsConstructor
public class PresentationWebConfig implements WebMvcConfigurer {

    private final LoginUserArgumentResolver loginUserArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }
}
