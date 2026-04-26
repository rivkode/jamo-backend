plugins {
    `java-library`
}

description = "공통 JWT 발급/검증 라이브러리 — RS256 + JWKS 친화 (ADR-0001). 모든 Java 서비스의 JWT 필터 / 토큰 발급 공유. Spring 의존 없음."

dependencies {
    api("com.nimbusds:nimbus-jose-jwt:9.41.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}
