description = "Identity service — auth, user, profile (ADR-0001, ADR-0002)"

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("identity-service")
}
