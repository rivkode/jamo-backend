plugins {
    java
    id("org.springframework.boot") version "3.5.14" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
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
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        "implementation"("org.springframework.boot:spring-boot-starter")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")

        // ArchUnit — 모듈 의존성 / 계층 자동 검증 (.claude/skills/module-boundary/references/archunit-rules.md)
        "testImplementation"("com.tngtech.archunit:archunit-junit5:1.3.0")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
