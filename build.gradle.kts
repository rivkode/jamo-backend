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
        // ArchUnit — 모듈 의존성 / 계층 자동 검증 (모든 모듈 공통)
        "testImplementation"("com.tngtech.archunit:archunit-junit5:1.3.0")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
