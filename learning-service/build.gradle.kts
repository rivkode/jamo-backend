description = "Learning service — sentence, word (ADR-0002, 첫 단계 비배포)"

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("learning-service")
}
