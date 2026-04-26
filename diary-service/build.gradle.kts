description = "Diary service — diary, comment, validation, diarychat, sentence-feedback (ADR-0002)"

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("diary-service")
}
