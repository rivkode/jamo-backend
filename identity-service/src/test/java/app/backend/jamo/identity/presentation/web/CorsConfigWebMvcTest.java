package app.backend.jamo.identity.presentation.web;

import app.backend.jamo.identity.infrastructure.config.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link CorsConfig} 슬라이스 검증 — Frontend SPA cross-origin 호출의 preflight(OPTIONS) 협상.
 *
 * <p><b>주의 (테스트 메커니즘)</b>: nested {@link ProbeController} 는 {@code @WebMvcTest(controllers=...)}
 * 로 지정해도 실제 GET 핸들러로 등록되지 않는다(실제 GET 은 404). 그래도 preflight 가 동작하는 이유는
 * CORS 협상이 핸들러 매핑과 무관하게 전역 {@code /api/**} 패턴으로 처리되기 때문 — 즉 본 테스트는
 * "핸들러 유무와 무관하게 preflight CORS 가 허용/차단된다"를 검증한다. 실제-요청의 ACAO echo 는 동일
 * {@code DefaultCorsProcessor} 가 같은 설정에서 파생하므로 별도 검증을 생략했다.
 */
@WebMvcTest(controllers = CorsConfigWebMvcTest.ProbeController.class)
@Import({CorsConfig.class, CorsConfigWebMvcTest.TestBeans.class})
class CorsConfigWebMvcTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";
    private static final String SECOND_ALLOWED_ORIGIN = "https://jamoai.app";
    private static final String DISALLOWED_ORIGIN = "http://evil.example.com";
    private static final String PROBE_PATH = "/api/v1/__cors-probe";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflight_from_allowed_origin_returns_cors_headers() throws Exception {
        mockMvc.perform(options(PROBE_PATH)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
    }

    @Test
    void preflight_from_second_allowed_origin_accepted() throws Exception {
        // 다중 origin 목록의 두번째 항목도 허용되는지 — allowedOrigins(...toArray) 변환 실증.
        mockMvc.perform(options(PROBE_PATH)
                        .header(HttpHeaders.ORIGIN, SECOND_ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, SECOND_ALLOWED_ORIGIN));
    }

    @Test
    void preflight_from_disallowed_origin_is_rejected() throws Exception {
        mockMvc.perform(options(PROBE_PATH)
                        .header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void preflight_with_disallowed_request_header_is_rejected() throws Exception {
        // allowedHeaders 화이트리스트(Authorization/Content-Type) 밖 헤더는 preflight 에서 거부.
        mockMvc.perform(options(PROBE_PATH)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Evil-Header"))
                .andExpect(status().isForbidden());
    }

    @Configuration
    static class TestBeans {
        @Bean
        CorsProperties corsProperties() {
            return new CorsProperties(List.of(ALLOWED_ORIGIN, SECOND_ALLOWED_ORIGIN));
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping(PROBE_PATH)
        String probe() {
            return "ok";
        }
    }
}
