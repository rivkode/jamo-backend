plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Chat service — chat (14 API) + AI 비즈니스 게이트웨이 (ADR-0002, ADR-0003)"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":contracts"))
    implementation(project(":common-auth-jwt"))
    implementation(project(":common-infrastructure"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("chat-service")
}
