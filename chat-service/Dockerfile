# syntax=docker/dockerfile:1.7
# chat-service 빌드 / 실행 이미지. diary-service/Dockerfile 패턴 정합 (SERVICE_NAME / EXPOSE 만 변경).

# ===== Stage 1: builder =====
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# (1) gradle wrapper + 루트 빌드 파일
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew

# (2) 모든 모듈의 build.gradle.kts 만 먼저 복사 — Gradle multi-project 평가용
COPY contracts/build.gradle.kts              ./contracts/
COPY common-auth-jwt/build.gradle.kts        ./common-auth-jwt/
COPY common-infrastructure/build.gradle.kts  ./common-infrastructure/
COPY identity-service/build.gradle.kts       ./identity-service/
COPY diary-service/build.gradle.kts          ./diary-service/
COPY chat-service/build.gradle.kts           ./chat-service/
COPY learning-service/build.gradle.kts       ./learning-service/
COPY platform-service/build.gradle.kts       ./platform-service/

# (3) 본 서비스가 의존하는 모듈 + 본 서비스 소스
COPY contracts             ./contracts
COPY common-auth-jwt       ./common-auth-jwt
COPY common-infrastructure ./common-infrastructure
COPY chat-service          ./chat-service

# (4) bootJar 만 빌드
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :chat-service:bootJar --no-daemon -x test \
 && cp chat-service/build/libs/chat-service-*.jar /workspace/app.jar

# ===== Stage 2: runtime =====
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

ENV TZ=Asia/Seoul

RUN useradd --system --create-home --uid 1001 spring

# TTS 음성 저장 디렉토리를 spring 소유로 선생성 — named volume 이 최초 마운트 시 소유권 상속.
RUN mkdir -p /app/audio-storage && chown spring:spring /app/audio-storage

COPY --from=builder --chown=spring:spring /workspace/app.jar /app/app.jar

USER spring

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
