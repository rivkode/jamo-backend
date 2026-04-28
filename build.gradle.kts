plugins {
    java
    id("org.springframework.boot") version "3.5.14" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.google.protobuf") version "0.9.4" apply false
}

allprojects {
    group = "app.backend.jamo"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        // Lombok — Application/Infrastructure/Presentation layer 의 보일러플레이트 감소 (ADR-0008 §B).
        // 도메인 layer 사용은 ArchUnit R10 으로 차단. compileOnly 라 contracts / common 모듈도 무해 (의존성만 보유).
        // 버전 명시: contracts / common-* 는 Spring Boot dependency-management plugin 미적용이라 BOM 자동 해석 X.
        "compileOnly"("org.projectlombok:lombok:1.18.34")
        "annotationProcessor"("org.projectlombok:lombok:1.18.34")
        "testCompileOnly"("org.projectlombok:lombok:1.18.34")
        "testAnnotationProcessor"("org.projectlombok:lombok:1.18.34")

        // ArchUnit — 모듈 의존성 / 계층 자동 검증 (모든 모듈 공통)
        "testImplementation"("com.tngtech.archunit:archunit-junit5:1.3.0")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
