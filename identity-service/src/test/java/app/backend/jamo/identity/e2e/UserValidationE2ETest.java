package app.backend.jamo.identity.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이메일 검증 흐름 E2E (PRD user/sendValidationNumber.md, user/validateEmail.md).
 *
 * <p>시나리오:
 * <ol>
 *   <li>send_stores_six_digit_code — 발송 후 Redis 에 6자리 코드 저장</li>
 *   <li>verify_with_correct_code_marks_flag — 검증 성공 시 email_validated flag 발급</li>
 *   <li>verify_with_wrong_code_returns_mismatch — 불일치 응답 + 잔여 시도 정보 비노출</li>
 *   <li>verify_locked_after_max_attempts — 5회 실패 시 LOCKED + 코드 invalidate</li>
 *   <li>verify_without_send_returns_expired — 발급되지 않은 이메일에 대한 검증</li>
 *   <li>send_with_invalid_email_returns_validation_failed — Bean Validation</li>
 *   <li>send_after_daily_limit_returns_rate_limited — daily limit 5회 도달</li>
 * </ol>
 *
 * <p>Testcontainers (MySQL + Redis). cooldown=PT0S 로 비활성화 — test 흐름의 30s 쿨다운 회피.
 * daily limit 은 default 5 유지로 rate limit 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestKeyConfig.class)
class UserValidationE2ETest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("identity")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // 테스트에서 30s 쿨다운 비활성화 — daily-limit 만으로 rate limit 검증
        registry.add("jamo.user-validation.cooldown", () -> "PT0S");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void send_validation_number_stores_six_digit_code_in_redis() throws Exception {
        String email = "e2e-send@example.com";

        mockMvc.perform(post("/api/v1/users/validation-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent());

        String code = redisTemplate.opsForValue().get("user:validation:code:" + email);
        assertThat(code).isNotNull().matches("\\d{6}");
    }

    @Test
    void verify_with_correct_code_marks_email_validated_flag_and_invalidates_code() throws Exception {
        String email = "e2e-success@example.com";
        sendCode(email);
        String code = redisTemplate.opsForValue().get("user:validation:code:" + email);

        mockMvc.perform(post("/api/v1/users/validation-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(redisTemplate.opsForValue().get("user:email_validated:" + email))
                .isEqualTo("1");
        assertThat(redisTemplate.opsForValue().get("user:validation:code:" + email))
                .isNull();
    }

    @Test
    void verify_with_wrong_code_returns_400_VALIDATION_CODE_MISMATCH_without_attempts_in_response()
            throws Exception {
        String email = "e2e-mismatch@example.com";
        sendCode(email);

        var result = mockMvc.perform(post("/api/v1/users/validation-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"999999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_CODE_MISMATCH"))
                .andExpect(jsonPath("$.attempts").doesNotExist())
                .andReturn();

        // 응답 body 에 attempts/숫자/raw exception 정보 누출 금지 (security review H2)
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("attempt", "1)", "999999");
    }

    @Test
    void verify_locked_after_5_failed_attempts_invalidates_code() throws Exception {
        String email = "e2e-locked@example.com";
        sendCode(email);

        // 1~4 mismatch
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/users/validation-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"code\":\"999999\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_CODE_MISMATCH"));
        }

        // 5번째 — locked + invalidate
        mockMvc.perform(post("/api/v1/users/validation-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"999999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_CODE_LOCKED"));

        assertThat(redisTemplate.opsForValue().get("user:validation:code:" + email)).isNull();
        assertThat(redisTemplate.opsForValue().get("user:validation:attempts:" + email)).isNull();
        assertThat(redisTemplate.opsForValue().get("user:email_validated:" + email)).isNull();
    }

    @Test
    void verify_without_send_returns_400_VALIDATION_CODE_EXPIRED() throws Exception {
        mockMvc.perform(post("/api/v1/users/validation-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"e2e-no-send@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_CODE_EXPIRED"));
    }

    @Test
    void send_with_invalid_email_returns_400_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(post("/api/v1/users/validation-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void send_after_daily_limit_returns_429_VALIDATION_RATE_LIMITED() throws Exception {
        String email = "e2e-daily@example.com";

        // daily-limit=5 도달 (cooldown=0 으로 빠른 호출)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/users/validation-number")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isNoContent());
        }

        // 6번째 — daily limit 초과
        mockMvc.perform(post("/api/v1/users/validation-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VALIDATION_RATE_LIMITED"));
    }

    private void sendCode(String email) throws Exception {
        mockMvc.perform(post("/api/v1/users/validation-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent());
    }
}
