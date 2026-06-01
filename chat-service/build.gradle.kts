plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Chat service — chat (14 API) + AI 비즈니스 게이트웨이 (ADR-0002, ADR-0003)"

dependencies {
    // ===== Spring Boot starters =====
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Redis — JWT 검증의 SessionBlacklist (logout/reuse 차단, common-auth-jwt BlacklistChecker).
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // springdoc-openapi (Swagger UI) — CLAUDE.md "새 서비스 OpenAPI 의무". prod 비활성(application.yaml).
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // ===== gRPC client (ai-service AiService 호출 — ADR-0003) =====
    implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")
    implementation("io.grpc:grpc-netty-shaded:1.66.0")
    // ===== gRPC server (AiAssistantService.GenerateChatResponse 노출 — diary-service diarychat AI, S4) =====
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
    // gRPC InProcess transport — ai-service gRPC client adapter 단위 테스트.
    testImplementation("io.grpc:grpc-inprocess:1.66.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("chat-service")
}
