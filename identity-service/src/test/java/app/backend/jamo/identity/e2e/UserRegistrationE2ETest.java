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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LOCAL 회원가입 E2E (PRD user/createUser.md).
 *
 * <p>시나리오:
 * <ol>
 *   <li>happy_path — flag pre-set → 201 + Redis flag 소비 + DB row 생성</li>
 *   <li>email_not_validated — flag 없음 → 400 EMAIL_NOT_VALIDATED</li>
 *   <li>email_already_registered — 동일 이메일 두 번째 가입 시도 → 409 EMAIL_ALREADY_REGISTERED
 *       (두 번째 시도도 flag 가 다시 mark 된 상태에서 시도 — flag 는 첫 시도에서 소비됨)</li>
 *   <li>password_too_short — Bean Validation 1차 차단</li>
 *   <li>flag_consumed_even_when_duplicate — flag 가 중복 검사보다 먼저 소비 (PRD §9 의도)</li>
 * </ol>
 *
 * <p>Testcontainers (MySQL + Redis). Redis 의 email_validated flag 는 직접 mark 후
 * createUser 진입 — sendValidationNumber/validateEmail 풀 시퀀스는 별도 E2E 에서 검증함.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestKeyConfig.class)
class UserRegistrationE2ETest {

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
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private StringRedisTemplate redisTemplate;

    private static final String EMAIL = "user@jamoai.app";
    private static final String FLAG_KEY_PREFIX = "user:email_validated:";

    @BeforeEach
    void resetState() throws Exception {
        // 단일 @BeforeEach — JUnit 5 가 동일 클래스 내 다중 lifecycle 의 실행 순서를 보장하지 않음.
        // 두 정리 작업을 한 메서드로 통합해 향후 의존성 추가 시도 silent flaky 차단 (test review H1).
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        try (var conn = mysql.createConnection("");
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM users");
        }
    }

    private void markEmailValidated(String email) {
        redisTemplate.opsForValue().set(FLAG_KEY_PREFIX + email, "1", Duration.ofMinutes(10));
    }

    private String body(String email, String password, String displayName) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"displayName\":\"" + displayName + "\"}";
    }

    @Test
    void register_happy_path_returns_201_and_consumes_flag_and_persists_user() throws Exception {
        markEmailValidated(EMAIL);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, "PlainPa$$w0rd", "jamo")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.displayName").value("jamo"))
                .andExpect(jsonPath("$.createdAt").exists());

        // flag 소비 확인 (PRD §9 — 같은 이메일 재가입 시도 차단)
        assertThat(redisTemplate.opsForValue().get(FLAG_KEY_PREFIX + EMAIL)).isNull();

        // DB 영속화 검증 — account_type=LOCAL, password_hash 가 BCrypt 해시 (평문 미저장),
        // display_name 정합 (test review M2). PreparedStatement 로 인라인 보간 회피 (test review M-new1).
        try (var conn = mysql.createConnection("");
             var ps = conn.prepareStatement(
                     "SELECT account_type, password_hash, display_name FROM users WHERE email = ?")) {
            ps.setString(1, EMAIL);
            try (var rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("account_type")).isEqualTo("LOCAL");
                String hash = rs.getString("password_hash");
                assertThat(hash).startsWith("$2a$12$").hasSize(60);
                assertThat(hash).doesNotContain("PlainPa$$w0rd");
                assertThat(rs.getString("display_name")).isEqualTo("jamo");
            }
        }
    }

    @Test
    void register_returns_400_EMAIL_NOT_VALIDATED_when_flag_absent() throws Exception {
        // flag 미발급 상태에서 가입 시도
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, "PlainPa$$w0rd", "jamo")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VALIDATED"));
    }

    @Test
    void register_returns_400_VALIDATION_FAILED_when_password_too_short() throws Exception {
        markEmailValidated(EMAIL);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, "short", "jamo")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Bean Validation 단계 차단 — flag 는 그대로 남아있어야 함
        assertThat(redisTemplate.opsForValue().get(FLAG_KEY_PREFIX + EMAIL)).isEqualTo("1");
    }

    @Test
    void register_returns_409_and_consumes_flag_when_local_email_already_registered() throws Exception {
        // 첫 가입 (성공)
        markEmailValidated(EMAIL);
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, "PlainPa$$w0rd", "jamo")))
                .andExpect(status().isCreated());
        assertThat(redisTemplate.opsForValue().get(FLAG_KEY_PREFIX + EMAIL)).isNull();

        // 두 번째 시도 — flag 다시 mark, 중복으로 fail 예상.
        // PRD §9 의도: flag 가 중복 검사 / save 실패와 무관하게 가장 먼저 소비.
        markEmailValidated(EMAIL);
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, "AnotherPa$$w0rd", "jamo2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        // flag 가 두 번째 시도에서도 소비됨 (의도된 trade-off — 재가입 시도 차단)
        assertThat(redisTemplate.opsForValue().get(FLAG_KEY_PREFIX + EMAIL)).isNull();
    }
}
