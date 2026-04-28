plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Diary service — diary, comment, validation, diarychat, sentence-feedback (ADR-0002)"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    // Application layer 트랜잭션 제어 (TransactionTemplate / @Transactional) — D-a-5-impl-app PR.
    // 후속 infra 슬라이스의 spring-boot-starter-data-jpa 가 spring-tx 를 transitive 포함하므로
    // 본 명시 의존성은 점진적 추가 패턴 (CLAUDE.md "사용자 제안 후 승인" 정합).
    implementation("org.springframework:spring-tx")
    implementation(project(":contracts"))
    implementation(project(":common-auth-jwt"))
    implementation(project(":common-infrastructure"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("diary-service")
}
