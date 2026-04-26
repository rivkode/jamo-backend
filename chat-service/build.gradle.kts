description = "Chat service — chat (14 API) + AI 비즈니스 게이트웨이 (ADR-0002, ADR-0003)"

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("chat-service")
}
