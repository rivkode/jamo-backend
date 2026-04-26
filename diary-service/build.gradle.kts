plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Diary service — diary, comment, validation, diarychat, sentence-feedback (ADR-0002)"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":contracts"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("diary-service")
}
