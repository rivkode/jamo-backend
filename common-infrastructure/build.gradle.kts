plugins {
    `java-library`
}

description = "공통 인프라 라이브러리 — 모든 Java 서비스의 MySQL/Redis/Kafka/Outbox/ProcessedEvent 공통 설정 + 유틸 (.claude/skills/module-boundary §4 참조). 첫 단계는 골격만, 실제 공통 코드는 use case 진행 시 추가."

dependencies {
    // 첫 단계는 의존성 없음 — 빈 java-library jar 만 생성.
    // 향후 추가 후보 (use case 진행 시):
    //   - api("org.springframework.boot:spring-boot-autoconfigure")  // @Configuration 공유
    //   - api("org.springframework.kafka:spring-kafka")              // Outbox publisher / Consumer base
    //   - api("org.springframework.data:spring-data-jpa")            // Outbox / ProcessedEvent JpaRepository

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}
