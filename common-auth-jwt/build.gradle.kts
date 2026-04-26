plugins {
    `java-library`
}

description = "공통 JWT 검증 라이브러리 — 모든 Java 서비스의 JWT 필터 / JWKS 캐싱 / Redis blacklist 검증 공유 (ADR-0001). 첫 단계는 골격만, JWT 라이브러리(예: nimbus-jose-jwt)는 실제 인증 use case 진행 시 추가."

dependencies {
    // 첫 단계는 의존성 없음 — 빈 java-library jar 만 생성.
    // 향후 추가 후보 (use case 진행 시):
    //   - api("com.nimbusds:nimbus-jose-jwt:9.41.2")  // JWT 검증 + JWKS
    //   - api("org.springframework:spring-context")    // @Component 등 가벼운 Spring 의존
    //   - api("org.springframework.data:spring-data-redis")  // blacklist 조회용

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}
