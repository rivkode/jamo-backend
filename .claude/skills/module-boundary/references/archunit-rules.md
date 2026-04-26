# ArchUnit 규칙 — jamo 멀티모듈 경계 자동 검증

`module-boundary/SKILL.md` §8 의 상세.

ArchUnit 으로 **컴파일/테스트 시점에 모듈 경계 위반을 자동 차단**한다. PR 리뷰 단계에서 사람이 매번 grep 하는 부담을 줄임.

> **Java 모듈만 적용.** `python-services/ai-service/` 는 ArchUnit 대상 아님 — 별도 검증 필요 (lint, 단위 테스트, gRPC 통합 테스트로 보강).

---

## 1. 규칙 카테고리

| # | 규칙 | 위반 시 |
|---|---|---|
| R1 | 다른 Java 서비스 모듈의 패키지를 import 하지 않음 | Critical (서비스 경계 침범) |
| R2 | `contracts` 모듈에 Spring/JPA 어노테이션 금지 | Critical |
| R3 | `domain.*` 패키지가 Spring/JPA 에 의존하지 않음 | Critical (DDD 원칙) |
| R4 | `presentation.*` 가 Repository 직접 의존하지 않음 (Application 경유) | High |
| R5 | `@GrpcService` (server) 는 `infrastructure/grpc/server/` 에만 | High |
| R6 | `@GrpcClient` 주입은 `infrastructure/grpc/client/` 에만 | High |
| R7 | proto 클래스 (`contracts.proto.*`) 가 `domain.*` / `application.*` 에 침투 금지 | Critical |
| R8 | chat-service 외 다른 서비스가 `contracts.proto.ai.*` (AiService) 를 import 하지 않음 | Critical (ADR-0003) |
| R9 | Kafka Consumer 메서드는 `infrastructure/messaging/` 에만 | Medium |
| R10 | JPA 연관관계 어노테이션 금지 (`@ManyToOne` / `@OneToMany` / `@OneToOne` / `@ManyToMany`) — ID 컬럼만 보유 (ADR-0005) | Critical |

---

## 2. 의존성 추가 (각 서비스 모듈의 `build.gradle.kts`)

```kotlin
testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
```

---

## 3. 규칙 구현 — diary-service 예시 (다른 서비스도 동일 패턴)

```java
// diary-service/src/test/java/app/backend/jamo/diary/architecture/ArchitectureTest.java
package app.backend.jamo.diary.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

@AnalyzeClasses(
    packages = "app.backend.jamo.diary",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
public class ArchitectureTest {

    // R1 — 다른 Java 서비스 패키지 import 금지
    @ArchTest
    static final ArchRule no_import_from_other_services =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.diary..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "app.backend.jamo.identity..",
                "app.backend.jamo.chat..",
                "app.backend.jamo.learning..",
                "app.backend.jamo.platform.."
            )
            .as("diary-service 는 다른 Java 서비스 모듈을 import 하지 않는다 (contracts / common-* 만 허용)");

    // R3 — domain 계층은 Spring/JPA 의존 금지
    @ArchTest
    static final ArchRule domain_should_not_depend_on_spring_or_jpa =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.diary.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "org.hibernate..",
                "com.fasterxml.jackson.."
            )
            .as("domain 계층은 프레임워크에 의존하지 않는다");

    // R10 — JPA 연관관계 어노테이션 금지 (ADR-0005). 외래 키 컬럼은 ID 만 보유.
    @ArchTest
    static final ArchRule no_jpa_relationship_annotations =
        noFields()
            .should().beAnnotatedWith(jakarta.persistence.ManyToOne.class)
            .orShould().beAnnotatedWith(jakarta.persistence.OneToMany.class)
            .orShould().beAnnotatedWith(jakarta.persistence.OneToOne.class)
            .orShould().beAnnotatedWith(jakarta.persistence.ManyToMany.class)
            .as("JPA 연관관계 어노테이션 금지 — ID 컬럼만 보유 (ADR-0005)");

    // R4 — presentation 은 Repository 인터페이스 직접 의존 금지
    @ArchTest
    static final ArchRule presentation_should_not_use_repository =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.diary.presentation..")
            .should().dependOnClassesThat()
            .resideInAPackage("app.backend.jamo.diary.domain.repository..")
            .as("Controller 는 Application Service 만 사용한다");

    // R5 — @GrpcService 는 infrastructure/grpc/server/ 에만
    @ArchTest
    static final ArchRule grpc_server_location =
        classes()
            .that().areAnnotatedWith("net.devh.boot.grpc.server.service.GrpcService")
            .should().resideInAPackage("..infrastructure.grpc.server..");

    // R6 — @GrpcClient 주입은 infrastructure/grpc/client/ 에만
    @ArchTest
    static final ArchRule grpc_client_injection_location =
        fields()
            .that().areAnnotatedWith("net.devh.boot.grpc.client.inject.GrpcClient")
            .should().beDeclaredInClassesThat()
            .resideInAPackage("..infrastructure.grpc.client..");

    // R7 — proto 클래스가 domain / application 에 침투 금지
    @ArchTest
    static final ArchRule proto_must_not_leak_to_domain_or_application =
        noClasses()
            .that().resideInAnyPackage(
                "app.backend.jamo.diary.domain..",
                "app.backend.jamo.diary.application.."
            )
            .should().dependOnClassesThat()
            .resideInAPackage("app.backend.jamo.contracts.proto..")
            .as("proto 생성 클래스는 infrastructure 에서만 사용 (Mapper 로 Domain 변환)");

    // R8 — chat-service 외 서비스가 ai proto 를 import 하지 않음
    //       (chat-service 는 본 규칙 제외 — chat-service 의 ArchitectureTest 에서는 R8' 사용)
    @ArchTest
    static final ArchRule no_direct_ai_service_import =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.diary..")
            .should().dependOnClassesThat()
            .resideInAPackage("app.backend.jamo.contracts.proto.ai..")
            .as("ai-service 의 AiService gRPC 는 chat-service 만 호출한다 (ADR-0003)");

    // R9 — @KafkaListener 는 infrastructure/messaging/ 에만
    @ArchTest
    static final ArchRule kafka_listener_location =
        methods()
            .that().areAnnotatedWith("org.springframework.kafka.annotation.KafkaListener")
            .should().beDeclaredInClassesThat()
            .resideInAPackage("..infrastructure.messaging..");
}
```

---

## 4. contracts 모듈 자체 규칙

```java
// contracts/src/test/java/app/backend/jamo/contracts/architecture/ContractsArchitectureTest.java
@AnalyzeClasses(packages = "app.backend.jamo.contracts")
public class ContractsArchitectureTest {

    // R2 — contracts 에 Spring / JPA 어노테이션 금지
    @ArchTest
    static final ArchRule no_spring_or_jpa_in_contracts =
        noClasses()
            .that().resideInAPackage("app.backend.jamo.contracts..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "org.hibernate..",
                "com.fasterxml.jackson.."
            )
            .as("contracts 모듈은 프레임워크 의존성 없는 순수 record + proto 만 보유");

    // 이벤트 record 는 모두 eventId / occurredAt 필드 보유 (수동 검증 권고)
    // → ArchUnit 으로 강제하기 까다로움. 대신 PR 리뷰 + 템플릿으로.
}
```

---

## 5. chat-service 만의 예외 규칙

chat-service 는 ai-service 를 호출하는 유일한 서비스이므로 R8 의 반대 규칙을 가져야 함:

```java
// chat-service/src/test/.../ArchitectureTest.java
// R8' — chat-service 는 ai proto 를 사용할 수 있되 infrastructure/grpc/client/ 에서만
@ArchTest
static final ArchRule ai_proto_only_in_grpc_client =
    classes()
        .that().resideInAPackage("app.backend.jamo.chat..")
        .and().dependOnClassesThat().resideInAPackage("app.backend.jamo.contracts.proto.ai..")
        .should().resideInAPackage("..infrastructure.grpc.client..");
```

---

## 6. CI 통합

```kotlin
// 각 서비스 build.gradle.kts
tasks.test {
    useJUnitPlatform()
}
```

PR 워크플로우에서 `./gradlew :diary-service:test --tests "*ArchitectureTest*"` 가 실패하면 머지 차단.

---

## 7. 미적용 영역 (수동 리뷰 필요)

ArchUnit 으로 자동화가 어려운 항목 — `code-reviewer` 에이전트가 grep / 수동 점검:

- **gRPC 호출의 Deadline 미설정 검사**: `withDeadlineAfter` 누락은 컴파일 통과. grep 또는 리뷰
- **Kafka Consumer 의 멱등성 처리**: `ProcessedEvent` 호출이 본문에 있는지는 ArchUnit 으로 검증 어려움
- **Outbox 패턴 사용 여부**: Application Service 가 직접 KafkaTemplate 호출하는지
- **proto field number 변경 / reserved 누락**: protoc 단계나 buf lint 도구로 보강
- **Python ai-service 의 코드 품질**: ArchUnit 미적용. 별도 lint (ruff, mypy 등)

---

## 8. 자가 검증 체크리스트

- [ ] 각 Java 서비스 모듈에 `ArchitectureTest` 존재
- [ ] R1~R9 모두 적용 (chat-service 는 R8 대신 R8' 사용)
- [ ] contracts 모듈에 별도 `ContractsArchitectureTest`
- [ ] CI 에서 ArchUnit 테스트 실행
- [ ] Python ai-service 는 별도 lint / 통합 테스트로 검증

---

## 9. 향후 보강

- **모듈 의존성 시각화**: ArchUnit 의 `Slice` API 로 의존 그래프 출력
- **순환 의존 검출**: ArchUnit `slices().should().beFreeOfCycles()`
- **Layer 검증** (DDD 4계층 의존 방향): `Architectures.layeredArchitecture()` 사용
- **proto 변경 감지**: contracts 의 `.proto` 파일 변경 시 양쪽 빌드 산출물 갱신 (Java + Python) — Gradle task / pre-commit hook (ADR-0003 Open Item)
