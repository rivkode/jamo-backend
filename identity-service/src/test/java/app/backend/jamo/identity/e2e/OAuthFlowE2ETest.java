package app.backend.jamo.identity.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * OAuth start → callback → exchange happy path E2E.
 *
 * Testcontainers (MySQL + Redis) + WireMock (provider stub) + TestKeyConfig (RSA dynamic).
 * 단일 happy path 만 검증 — 나머지 분기는 @WebMvcTest + Application 단위 테스트로 커버.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestKeyConfig.class)
class OAuthFlowE2ETest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("identity")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static final WireMockServer wireMock =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        registry.add("jamo.oauth.providers.kakao.token-url", () -> wireMock.baseUrl() + "/kakao/token");
        registry.add("jamo.oauth.providers.kakao.userinfo-url", () -> wireMock.baseUrl() + "/kakao/userinfo");
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Test
    void kakao_oauth_full_flow_happy_path() throws Exception {
        // ====================================================================
        // 1) START — 302 + Set-Cookie oauth_state_kakao + Set-Cookie jamo_device_id
        // ====================================================================
        MvcResult startResult = mockMvc.perform(get("/api/v1/auth/oauth/kakao/start"))
                .andReturn();

        assertThat(startResult.getResponse().getStatus()).isEqualTo(302);
        String authorizeUrl = startResult.getResponse().getHeader("Location");
        assertThat(authorizeUrl).contains("response_type=code");
        assertThat(authorizeUrl).contains("client_id=test-kakao-client");

        Cookie stateCookie = startResult.getResponse().getCookie("oauth_state_kakao");
        assertThat(stateCookie).isNotNull();
        assertThat(stateCookie.getValue()).isNotBlank();
        String stateValue = stateCookie.getValue();

        Cookie deviceCookie = startResult.getResponse().getCookie("jamo_device_id");
        assertThat(deviceCookie).isNotNull();
        assertThat(deviceCookie.getValue()).startsWith("web-");

        // ====================================================================
        // 2) Provider stub — token + userinfo
        // ====================================================================
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/kakao/token")
                .willReturn(okJson("""
                        {"access_token":"kakao-access-token","token_type":"bearer","expires_in":21599}
                        """)));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/kakao/userinfo")
                .withHeader("Authorization", equalTo("Bearer kakao-access-token"))
                .willReturn(okJson("""
                        {"id":987654321,
                         "properties":{"nickname":"jamo-e2e"},
                         "kakao_account":{"email":"e2e@kakao.test"}}
                        """)));

        // ====================================================================
        // 3) CALLBACK — 302 + Location frontend/auth/callback?code=AUTHCODE
        // ====================================================================
        MvcResult callbackResult = mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                        .param("code", "kakao-authorize-code")
                        .param("state", stateValue)
                        .cookie(new Cookie("oauth_state_kakao", stateValue)))
                .andReturn();

        assertThat(callbackResult.getResponse().getStatus()).isEqualTo(302);
        String frontendRedirect = callbackResult.getResponse().getHeader("Location");
        assertThat(frontendRedirect).startsWith("http://app.test/auth/callback");
        assertThat(frontendRedirect).contains("isNew=true");

        // state cookie 가 clear 됐는지 확인 (Max-Age=0)
        Cookie clearedState = callbackResult.getResponse().getCookie("oauth_state_kakao");
        assertThat(clearedState).isNotNull();
        assertThat(clearedState.getMaxAge()).isZero();

        String authCode = extractQueryParam(frontendRedirect, "code");
        assertThat(authCode).isNotBlank();

        // ====================================================================
        // 4) EXCHANGE — 200 + JWT 페어
        // ====================================================================
        MvcResult exchangeResult = mockMvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", authCode))))
                .andReturn();

        assertThat(exchangeResult.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(exchangeResult.getResponse().getContentAsString());
        assertThat(body.get("userId").asText()).isNotBlank();
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("refreshToken").asText()).isNotBlank();
        assertThat(body.get("expiresInSeconds").asLong()).isEqualTo(900L);

        // 같은 authCode 로 두 번째 exchange 시도 → 401 (one-time consume 검증)
        MvcResult secondExchange = mockMvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", authCode))))
                .andReturn();
        assertThat(secondExchange.getResponse().getStatus()).isEqualTo(401);
        JsonNode errorBody = objectMapper.readTree(secondExchange.getResponse().getContentAsString());
        assertThat(errorBody.get("code").asText()).isEqualTo("AUTH_CODE_INVALID");
    }

    private static String extractQueryParam(String url, String paramName) {
        URI uri = URI.create(url);
        String query = uri.getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(paramName)) {
                return kv[1];
            }
        }
        return null;
    }
}
