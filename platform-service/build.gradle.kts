description = "Platform service — shorts, event(랭킹 ZSET), feedback (ADR-0002)"

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("platform-service")
}
