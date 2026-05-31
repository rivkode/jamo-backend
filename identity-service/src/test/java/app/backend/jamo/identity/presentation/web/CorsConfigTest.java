package app.backend.jamo.identity.presentation.web;

import app.backend.jamo.identity.infrastructure.config.CorsProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link CorsConfig} 의 {@code CorsProperties} 부재 시 no-op 방어 분기 단위 검증.
 *
 * <p>이 분기는 설정의 존재 이유 자체다 — {@code WebMvcConfigurer} 가 모든 {@code @WebMvcTest} 슬라이스에
 * 자동 포함되는데 그 슬라이스엔 {@code CorsProperties} 빈이 없다. {@link ObjectProvider} no-op 이 없으면
 * 모든 컨트롤러 슬라이스가 빈 주입 실패로 깨진다. 슬라이스로는 다른 web 빈(JwtVerifier 등) 의존이 얽혀
 * 검증이 취약하므로, {@link CorsRegistry} 를 mock 해 분기를 직접 고정한다.
 */
class CorsConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void addCorsMappings_is_noop_when_properties_bean_absent() {
        ObjectProvider<CorsProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        CorsRegistry registry = mock(CorsRegistry.class);

        new CorsConfig(provider).addCorsMappings(registry);

        verifyNoInteractions(registry);  // 빈 없으면 어떤 매핑도 등록하지 않음
    }

    @Test
    @SuppressWarnings("unchecked")
    void addCorsMappings_registers_api_mapping_when_properties_present() {
        ObjectProvider<CorsProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(new CorsProperties(List.of("http://localhost:3000")));
        // addMapping 이 반환하는 CorsRegistration 은 fluent self-return (allowedOrigins/Methods... 체인).
        CorsRegistration registration = mock(CorsRegistration.class, Answers.RETURNS_SELF);
        CorsRegistry registry = mock(CorsRegistry.class);
        when(registry.addMapping("/api/**")).thenReturn(registration);

        new CorsConfig(provider).addCorsMappings(registry);

        verify(registry).addMapping("/api/**");  // 빈 있으면 /api/** 정책 등록
    }
}
