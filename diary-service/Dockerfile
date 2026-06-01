# syntax=docker/dockerfile:1.7
# diary-service 빌드 / 실행 이미지.
# identity-service/Dockerfile 패턴 정합 — SERVICE_NAME / EXPOSE 만 변경.

# ===== Stage 1: builder =====
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# (1) gradle wrapper + 루트 빌드 파일 (의존성 캐시 layer 분리)
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

# (3) 본 서비스가 의존하는 모듈 + 본 서비스 소스 — 다른 서비스 src 는 불필요
COPY contracts             ./contracts
COPY common-auth-jwt       ./common-auth-jwt
COPY common-infrastructure ./common-infrastructure
COPY diary-service         ./diary-service

# (4) bootJar 만 빌드 (테스트는 CI 에서 별도 실행)
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :diary-service:bootJar --no-daemon -x test \
 && cp diary-service/build/libs/diary-service-*.jar /workspace/app.jar

# ===== Stage 2: runtime =====
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

ENV TZ=Asia/Seoul

RUN useradd --system --create-home --uid 1001 spring

# 음성 저장 디렉토리를 spring 소유로 미리 생성 — named volume(/app/audio-storage) 이 최초 마운트 시
# 이미지의 소유권을 그대로 상속하므로, 비root(spring) 컨테이너가 업로드 파일을 write 할 수 있다.
RUN mkdir -p /app/audio-storage && chown spring:spring /app/audio-storage

COPY --from=builder --chown=spring:spring /workspace/app.jar /app/app.jar

USER spring

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
