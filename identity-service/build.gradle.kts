plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Identity service — auth, user, profile (ADR-0001, ADR-0002)"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(project(":contracts"))
    implementation(project(":common-auth-jwt"))
    implementation(project(":common-infrastructure"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("identity-service")
}
