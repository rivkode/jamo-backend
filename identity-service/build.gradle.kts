plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Identity service — auth, user, profile (ADR-0001, ADR-0002)"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // BCryptPasswordEncoder 만 사용 — full Spring Security 미포함 (decisions/identity/local-credential-modeling.md)
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    // springdoc-openapi (Swagger UI). prod profile 에서는 application.yaml 로 비활성.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    runtimeOnly("com.mysql:mysql-connector-j")

    implementation(project(":contracts"))
    implementation(project(":common-auth-jwt"))
    implementation(project(":common-infrastructure"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    // WireMock — OAuth provider stub (ADR-0006 결정 5: HttpOAuthProviderClient 단위 테스트)
    testImplementation("org.wiremock:wiremock-standalone:3.9.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("identity-service")
}
