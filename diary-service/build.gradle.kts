plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Diary service — diary, comment, validation, diarychat, sentence-feedback (ADR-0002)"

dependencies {
    // ===== Spring Boot starters =====
    // web: 부팅 + actuator health + sentence-feedback HTTP API (D-a-5-impl-presentation). starter-web
    // 흡수로 jackson-datatype-jsr310 / spring-tx 도 transitive 확보 → PR #64 의 명시 spring-tx 라인 정리.
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Bean Validation (Jakarta Validation API) — Controller request DTO @Valid (D-a-5-impl-presentation).
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Redis — sentence-feedback rate limit (분 10 / 일 50, identity ValidationRateLimiter 패턴 정합).
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // springdoc-openapi (Swagger UI) — CLAUDE.md "새 서비스 OpenAPI 의무" (controller 첫 도입 PR).
    // prod profile 에서 application.yaml 로 비활성.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // Flyway (CLAUDE.md V1~Vn 마이그레이션 의무) — flyway-mysql 필수 (MySQL 8 + Flyway 10).
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    runtimeOnly("com.mysql:mysql-connector-j")

    // ===== Kafka (Outbox publisher + Saga consumer) =====
    implementation("org.springframework.kafka:spring-kafka")

    // ===== gRPC client (chat-service AiAssistantService 호출) =====
    // contracts 모듈이 grpc-stub / grpc-protobuf 를 api 로 노출. 본 모듈은 Spring 통합 + transport 만 추가.
    implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")
    implementation("io.grpc:grpc-netty-shaded:1.66.0")

    // ===== gRPC server (DiaryQueryService 노출 — identity-service 의 diaryCount 조회) =====
    // diary-service 가 처음으로 gRPC server 역할 (Slice 3-b / PRD 0526_flutter.md §1.5·§1.6).
    implementation("net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE")

    // ===== Resilience4j (CLAUDE.md NEVER: Circuit Breaker/Retry/Fallback 미설정) =====
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.2.0")

    // ===== 모듈 의존성 =====
    implementation(project(":contracts"))
    implementation(project(":common-auth-jwt"))
    implementation(project(":common-infrastructure"))

    // ===== 테스트 =====
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.kafka:spring-kafka-test")  // EmbeddedKafkaBroker
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    // gRPC InProcess transport — adapter 단위 테스트 (server stub mock impl 패턴).
    // grpc-netty-shaded 는 netty transport 만, InProcessServerBuilder 는 별 artifact.
    testImplementation("io.grpc:grpc-inprocess:1.66.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("diary-service")
}

tasks.named<Test>("test") {
    // Testcontainers Ryuk (컨테이너 누수 cleanup) 는 docker.sock bind mount 가 필요. Colima / Rancher 등
    // 일부 macOS docker 환경에서 mount 실패. GenericContainer.stop() 이 자체 cleanup 하므로 비활성 안전.
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}
