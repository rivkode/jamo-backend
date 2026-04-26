plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Learning service — sentence, word (ADR-0002, 첫 단계 비배포)"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":contracts"))
    implementation(project(":common-auth-jwt"))
    implementation(project(":common-infrastructure"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("learning-service")
}
