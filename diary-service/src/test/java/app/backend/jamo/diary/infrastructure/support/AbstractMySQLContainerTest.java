package app.backend.jamo.diary.infrastructure.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * @DataJpaTest 슬라이스용 MySQL Testcontainers base. JSON 컬럼 / Flyway V5 검증을 위해 H2 미사용.
 *
 * <p><b>Singleton container 패턴</b>: {@code @Testcontainers + @Container} 는 클래스마다 시작/종료
 * lifecycle — 두 DataJpaTest 클래스가 순차 실행 시 컨테이너 종료/재시작 race 로 EntityManager 가
 * 못 열림. 정적 초기화 블록에서 한 번만 시작 후 JVM 종료까지 유지 — Testcontainers 권장
 * <a href="https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/#singleton-containers">singleton</a>
 * 패턴.
 */
public abstract class AbstractMySQLContainerTest {

    @SuppressWarnings("resource")
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
        .withDatabaseName("diary")
        .withUsername("test")
        .withPassword("test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
