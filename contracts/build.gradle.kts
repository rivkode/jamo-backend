import com.google.protobuf.gradle.id

plugins {
    `java-library`
    id("com.google.protobuf")
}

description = "Shared gRPC proto + Kafka events (ADR-0002, ADR-0003). Java 빌드 + Python ai-service 빌드 양쪽 입력."

dependencies {
    // Protobuf / gRPC — proto 생성 결과물이 의존하므로 api 노출
    api("com.google.protobuf:protobuf-java:4.28.2")
    api("io.grpc:grpc-stub:1.66.0")
    api("io.grpc:grpc-protobuf:1.66.0")

    // Java 9+ 에서 javax.annotation.Generated 누락 경고 회피 (proto 생성 코드용)
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    // contracts 는 dep-management plugin 없음 → junit version 명시 (5 서비스 BOM 과 일치)
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.28.2"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.66.0"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
            }
        }
    }
}
