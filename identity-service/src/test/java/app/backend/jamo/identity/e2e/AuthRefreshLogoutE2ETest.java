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
import org.springframework.http.HttpHeaders;
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
 * Refresh 회전 + reuse detection + logout E2E.
 *
 * <p>OAuth → exchange 로 첫 access/refresh 페어 발급 → 본 클래스의 4 시나리오:
 * <ol>
 *   <li>{@code refresh_rotation_happy_path} — 신규 페어 발급 + 구 refresh 폐기</li>
 *   <li>{@code reuse_attempt_returns_refresh_invalid} — 폐기된 구 refresh 재사용 시 401</li>
 *   <li>{@code reuse_compensation_revokes_user_new_access_too} — reuse 보상으로 신규 access 도 거부</li>
 *   <li>{@code logout_blacklists_sid_so_subsequent_access_is_rejected} — logout 후 같은 access
 *       JWT 로 다시 logout 호출 시 401 (sid blacklist)</li>
 * </ol>
 *
 * Testcontainers (MySQL + Redis) + WireMock + TestKeyConfig (RSA dynamic).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestKeyConfig.class)
class AuthRefreshLogoutE2ETest {

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
        stubKakaoProvider("e2e-rotation@kakao.test", "rotate-user");
    }

    private void stubKakaoProvider(String email, String nickname) {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/kakao/token")
                .willReturn(okJson("""
                        {"access_token":"kakao-access-token","token_type":"bearer","expires_in":21599}
                        """)));
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/kakao/userinfo")
                .withHeader("Authorization", equalTo("Bearer kakao-access-token"))
                .willReturn(okJson("""
                        {"id":111111111,
                         "properties":{"nickname":"%s"},
                         "kakao_account":{"email":"%s"}}
                        """.formatted(nickname, email))));
    }

    /** OAuth start → callback → exchange — 토큰 페어 발급 후 응답 JsonNode 반환. */
    private JsonNode performOAuthAndExchange() throws Exception {
        MvcResult start = mockMvc.perform(get("/api/v1/auth/oauth/kakao/start")).andReturn();
        Cookie stateCookie = start.getResponse().getCookie("oauth_state_kakao");
        assertThat(stateCookie).isNotNull();
        String state = stateCookie.getValue();

        MvcResult callback = mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                        .param("code", "kakao-authorize-code")
                        .param("state", state)
                        .cookie(new Cookie("oauth_state_kakao", state)))
                .andReturn();
        String redirect = callback.getResponse().getHeader("Location");
        String authCode = extractQueryParam(redirect, "code");

        MvcResult exchange = mockMvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", authCode))))
                .andReturn();
        assertThat(exchange.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(exchange.getResponse().getContentAsString());
    }

    @Test
    void refresh_rotation_happy_path() throws Exception {
        JsonNode initial = performOAuthAndExchange();
        String firstAccess = initial.get("accessToken").asText();
        String firstRefresh = initial.get("refreshToken").asText();
        assertThat(firstAccess).isNotBlank();
        assertThat(firstRefresh).isNotBlank();

        // refresh 호출 — 신규 페어 발급
        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefresh))))
                .andReturn();
        assertThat(refresh.getResponse().getStatus()).isEqualTo(200);
        JsonNode rotated = objectMapper.readTree(refresh.getResponse().getContentAsString());
        assertThat(rotated.get("accessToken").asText()).isNotBlank().isNotEqualTo(firstAccess);
        assertThat(rotated.get("refreshToken").asText()).isNotBlank().isNotEqualTo(firstRefresh);
        assertThat(rotated.get("userId").asText()).isEqualTo(initial.get("userId").asText());
    }

    @Test
    void reuse_attempt_returns_refresh_invalid() throws Exception {
        JsonNode initial = performOAuthAndExchange();
        String firstRefresh = initial.get("refreshToken").asText();

        // 1차 회전 — 정상
        MvcResult firstRotation = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefresh))))
                .andReturn();
        assertThat(firstRotation.getResponse().getStatus()).isEqualTo(200);

        // 폐기된 firstRefresh 재사용 — 401 REFRESH_INVALID (REUSE 신호 비노출)
        MvcResult reuseAttempt = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefresh))))
                .andReturn();
        assertThat(reuseAttempt.getResponse().getStatus()).isEqualTo(401);
        JsonNode error = objectMapper.readTree(reuseAttempt.getResponse().getContentAsString());
        assertThat(error.get("code").asText()).isEqualTo("REFRESH_INVALID");
    }

    @Test
    void reuse_compensation_revokes_user_new_access_too() throws Exception {
        JsonNode initial = performOAuthAndExchange();
        String firstRefresh = initial.get("refreshToken").asText();

        MvcResult firstRotation = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefresh))))
                .andReturn();
        JsonNode rotated = objectMapper.readTree(firstRotation.getResponse().getContentAsString());
        String secondAccess = rotated.get("accessToken").asText();

        // reuse 트리거 — 보상 트랜잭션이 신규 sid 도 함께 폐기
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefresh))));

        // 신규 access JWT 도 blacklist 등록 → 보호 endpoint 호출 시 401
        MvcResult protectedCall = mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAccess))
                .andReturn();
        assertThat(protectedCall.getResponse().getStatus()).isEqualTo(401);
        JsonNode protectedError = objectMapper.readTree(protectedCall.getResponse().getContentAsString());
        assertThat(protectedError.get("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void logout_blacklists_sid_so_subsequent_access_is_rejected() throws Exception {
        JsonNode initial = performOAuthAndExchange();
        String accessToken = initial.get("accessToken").asText();

        // logout — 204
        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andReturn();
        assertThat(logout.getResponse().getStatus()).isEqualTo(204);

        // 동일 access 로 다시 logout — 401 (sid blacklisted)
        MvcResult secondLogout = mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andReturn();
        assertThat(secondLogout.getResponse().getStatus()).isEqualTo(401);
        JsonNode error = objectMapper.readTree(secondLogout.getResponse().getContentAsString());
        assertThat(error.get("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    private static String extractQueryParam(String url, String paramName) {
        URI uri = URI.create(url);
        String query = uri.getQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(paramName)) return kv[1];
        }
        return null;
    }
}
