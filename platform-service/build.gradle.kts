plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Platform service — shorts, event(랭킹 ZSET), feedback (ADR-0002)"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":contracts"))
    implementation(project(":common-auth-jwt"))
    implementation(project(":common-infrastructure"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("platform-service")
}
