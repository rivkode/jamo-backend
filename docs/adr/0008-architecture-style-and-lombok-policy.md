# ADR-0008: 아키텍처 스타일 — Hexagonal in/out 금지 + Lombok 화이트리스트 도입

- **상태**: Accepted
- **결정일**: 2026-04-29
- **결정자**: jonghun
- **관련 ADR**: [ADR-0002 서비스 분할](0002-service-decomposition.md), [ADR-0005 JPA 연관관계 금지](0005-no-jpa-associations.md)
- **관련 SKILL**: [`.claude/skills/ddd-architecture/SKILL.md`](../../.claude/skills/ddd-architecture/SKILL.md), [`.claude/skills/module-boundary/SKILL.md`](../../.claude/skills/module-boundary/SKILL.md)

## 컨텍스트

본 프로젝트는 Phase 6-b (profile App+Infra, PR #44) 시점부터 Application Service 패턴 (TransactionTemplate + 명시적 생성자 + `Objects.requireNonNull`) 을 관성으로 적용해 왔다. 그러나 다음 두 결정은 **명시적 박제 없이** 첫 슬라이스의 패턴이 누적되었음 — D-a-5-impl-app (PR #64) 진입 시점에 사용자가 명시적 결정을 요구함:

1. **아키텍처 스타일** — DDD Layered (CLAUDE.md "Domain ← Application ← Infrastructure / Presentation") 는 박제됨. 그러나 **Hexagonal Architecture** 의 inbound port (Application Service interface) / outbound port (도메인 외부 시스템 추상화) / port-adapter 디렉토리 분리 (`port/in/`, `port/out/`, `adapter/in/`, `adapter/out/`) 는 도입 여부 미결정. 현재까지 모든 port (Repository / PasswordEncoder / EmailSender / OutboxEventPublisher / SentenceFeedbackAiGateway 등) 가 `domain/repository/` 평면에 위치.
2. **Lombok 사용 여부** — 모든 Service / Controller / Adapter 가 명시적 생성자 + `Objects.requireNonNull` 패턴. 보일러플레이트 (~5-10줄/클래스) 누적. Lombok 의존성은 build.gradle.kts 어디에도 없음.

본 ADR 은 두 결정을 함께 박제 — 두 결정이 같은 의미 ("코드 표현 명시성" 정책) 를 공유하므로 단일 ADR 에 묶음. 사용자 결정은 다음과 같다:
- Hexagonal in/out 용어가 헷갈려 절대 사용하지 않겠다 → **금지 박제**.
- Application / Infrastructure / Presentation 의 보일러플레이트 감소 → **Lombok 화이트리스트 도입**.

## 검토한 옵션

### A. 아키텍처 스타일

#### Option A1 — DDD Layered (단순 의존성 방향) **(채택)**

```
diary/
├── domain/
│   ├── model/<aggregate>/
│   ├── repository/    ← 모든 port (Repository + 외부 시스템) 평면
│   └── exception/
├── application/
│   ├── dto/
│   └── service/
├── infrastructure/
│   ├── persistence/
│   ├── grpc/
│   └── messaging/
└── presentation/
    ├── controller/
    └── dto/
```

- 모든 port = `domain/repository/` 평면. inbound port (Application Service interface) 미도입 — Service 클래스 직접 사용.
- 의존성 방향만 강제: Domain ← Application ← Infrastructure / Presentation (CLAUDE.md "핵심 원칙 #2").

**장점**:
- 단순. 신규 기여자 학습 비용 낮음.
- 패키지 구조가 적은 깊이 (4-level: `<service>.{domain|application|...}.<sub>`).
- 다른 서비스 (identity / chat / learning / platform) 와 일관 — 본 결정 시점까지 모두 이 구조.
- ArchUnit 규칙이 단순 (R1 다른 서비스 import 차단, R3 domain Spring/JPA 의존 차단 — 충분).

**단점**:
- "Repository" 디렉토리에 외부 시스템 추상화 (PasswordEncoder / EmailSender / AiGateway) 도 함께 있어 의미 혼재. 단 본 시점 명명만 모호 — 동작 영향 X.

#### Option A2 — Hexagonal Architecture (port/adapter 분리 + inbound/outbound)

```
diary/
├── domain/
│   ├── model/
│   └── port/
│       ├── in/          ← inbound (use case) port — Application Service interface
│       └── out/         ← outbound port (Repository / Gateway / Encoder)
├── application/
│   └── service/         ← in port 구현
└── adapter/
    ├── in/              ← Controller / gRPC server / Kafka Consumer
    └── out/             ← JPA Repository / gRPC client / Encoder 구현
```

**장점**:
- port-adapter 의 책임 분리가 디렉토리에 시각적으로 표현.
- in/out 용어가 학습 커뮤니티 (Cockburn / Buschmann) 에서 표준.
- 테스트 시 mocking 대상이 in / out 으로 자연 분류.

**단점 (거부 이유)**:
- **사용자 명시 거부** — "in / out 용어가 헷갈리는 표현, 절대 사용하고 싶지 않다".
- 본 프로젝트는 이미 5 서비스가 DDD Layered 채택. 한 서비스만 Hexagonal 로 가면 일관성 깨짐.
- inbound port (Service interface + 구현 분리) 는 본 프로젝트 규모에서 가치 적음 — Service 가 단일 구현, mock 은 Repository / Gateway 만으로 충분.
- 디렉토리 깊이 증가 (5-level: `<service>.<port|adapter>.{in|out}.<sub>`) — IDE 네비게이션 / 파일 탐색 오버헤드.
- "in / out" 명명이 도메인 의미 (use case / 외부 시스템) 보다 기술적 (방향) — DDD 도메인 우선 원칙과 약한 충돌.

### B. Lombok 사용

#### Option B1 — Lombok 미도입 (현재 관성)

**장점**:
- 의존성 0 (annotation processor 추가 X).
- 코드 표면이 SoT — 자동 생성 코드 추측 불요.
- IDE 호환성 100% (Lombok plugin 미요구).

**단점 (거부 이유)**:
- Application Service / Controller 의 생성자 보일러플레이트 ~5-10줄/클래스 (이미 6+ 클래스 누적, 후속 더 늘어남).
- final field 강제가 자발적 (`Objects.requireNonNull` 누락 위험).
- 사용자 명시 — Lombok 도입 의향.

#### Option B2 — Lombok 화이트리스트 도입 **(채택)**

화이트리스트 / 블랙리스트 명시:

| Annotation | 사용 가능 영역 | 근거 |
|---|---|---|
| `@RequiredArgsConstructor` | Application Service / Infrastructure Adapter / Presentation Controller (Spring DI 대상) | 보일러플레이트 감소, final field 강제 + 생성자 자동 |
| `@Slf4j` | 모든 layer (도메인 제외) | 로깅 표준 |
| `@Builder` | 복잡한 DTO 만 (Application Result / Presentation Response 의 7+ 필드) | 가독성 (선택적 사용) |
| `@Getter` | **JPA Entity 한정** (`infrastructure/persistence/entity/*JpaEntity.java`) — JPA spec 이 record 미지원 강제 | record 가능 영역은 record 우선 |

| 금지 (블랙리스트) | 영역 | 근거 |
|---|---|---|
| `@Data` | 전 layer | setter 자동 추가 — CLAUDE.md "public setter 금지" 위반 |
| `@Setter` (클래스 레벨) | 전 layer | 동일 |
| `@AllArgsConstructor` | 전 layer | 모든 필드 노출 — 도메인 invariant / DTO 불변성 우회 위험 |
| `@NoArgsConstructor` | 도메인 | invariant 검증 우회 |
| 모든 Lombok annotation | **도메인 layer** (`domain/`) | 본 ADR §C 5가지 근거 |

#### Option B3 — Lombok 풀 도입 (`@Data` 등 자유)

**거부 이유**:
- `@Data` / `@Setter` 가 자동 setter 추가 → CLAUDE.md "public setter 금지" 위반.
- 자동 `equals` / `hashCode` 가 모든 필드 기반 → Aggregate Root 의 id-based equals 정책과 충돌 가능.

## 결정

### A. **Hexagonal in/out 용어 금지** (Option A1 채택)

1. **DDD Layered 4-디렉토리 구조 유지** — `domain / application / infrastructure / presentation`.
2. **port 위치 = `domain/repository/` 평면** — Repository / PasswordEncoder / EmailSender / OutboxEventPublisher / SentenceFeedbackAiGateway 등 모든 외부 추상화가 한 디렉토리.
3. **inbound port 미도입** — Application Service 는 클래스 직접 노출 (interface + impl 분리 X).
4. **금지 용어**: `inbound port` / `outbound port` / `inbound adapter` / `outbound adapter` / `port/in/` / `port/out/` / `adapter/in/` / `adapter/out/`.
5. **금지 디렉토리**: `<service>/port/`, `<service>/adapter/`.
6. JavaDoc / 박제 결정 로그 / 커밋 메시지 / PR 본문 등 **본 프로젝트 전 영역**에서 위 용어 사용 금지. 동의어 (gateway / client / encoder / repository) 는 사용 가능.

### B. **Lombok 화이트리스트 도입** (Option B2 채택)

1. 의존성 추가:
   ```kotlin
   // build.gradle.kts (subprojects 공통 또는 각 서비스 build.gradle.kts)
   compileOnly("org.projectlombok:lombok")
   annotationProcessor("org.projectlombok:lombok")
   testCompileOnly("org.projectlombok:lombok")
   testAnnotationProcessor("org.projectlombok:lombok")
   ```
2. 화이트리스트 / 블랙리스트는 위 §B Option B2 표 그대로 박제.
3. **기존 코드 마이그레이션** 은 별도 PR (Step 2). 본 ADR 은 **정책 박제만**, 코드 변경 0.

### C. 도메인 layer Lombok 미적용 — 5가지 근거

도메인 (`<service>/domain/`) 은 Lombok annotation 사용 금지. 이유:

#### 1. 순수 Java / 의존성 최소화

도메인 = 비즈니스 로직 핵심. 외부 라이브러리 의존이 적을수록:
- **이식성** ↑ — 컨테이너 / 프레임워크 / 빌드 도구 변경 시 도메인은 그대로 옮길 수 있음.
- **테스트 단순성** ↑ — annotation processor 없이 컴파일 가능 → IDE / 빌드 도구 호환성 ↑.
- **DDD 원칙 정합** — Eric Evans / Vaughn Vernon 모두 도메인 = pure business logic 강조 (`Implementing DDD` Ch.2 "Layers").

#### 2. Invariant 검증의 명시성

도메인의 compact constructor / 정적 팩토리는 invariant 검증 위치 (예시 — 본 프로젝트 `SentenceText` VO):

```java
public SentenceText {
    Objects.requireNonNull(value, "value");
    if (value.isEmpty() || isWhitespaceOnly(value)) {
        throw new InvalidSentenceTextException("sentence must not be blank");
    }
    int codePoints = value.codePointCount(0, value.length());
    if (codePoints > MAX_CODE_POINTS) {
        throw new InvalidSentenceTextException(
            "sentence length out of range: max " + MAX_CODE_POINTS + ", got " + codePoints
        );
    }
}
```

- Lombok `@NonNull` 은 단순 null 검증만 — 도메인 invariant 는 길이 / 화이트리스트 / 범위 / 상태 전이 등 풍부.
- **명시 작성이 의도 + 위치 + 메시지를 한 곳에 모음** → 디버깅 / 리뷰 / 학습 친화.

#### 3. 캡슐화 보호

CLAUDE.md "❌ public setter (상태 변경은 의미 있는 메서드로)". `@Data` / `@Setter` 가 자동 setter 추가하면 도메인 캡슐화 위반. `@Getter` 만 사용해도 record 와 비교 시 이득 적음 (record 가 이미 자동 accessor + immutable + equals/hashCode + toString).

#### 4. 자동 생성 코드의 SoT 분산

Lombok 자동 생성 코드는 컴파일 타임에만 존재 → 소스 표면에 안 보임. 도메인은 invariant / 라이프사이클 / 메서드 시그니처 모두 명시되어야 학습 / 변경 / 리뷰가 명확. **자동 생성은 Single Source of Truth 분산**.

#### 5. 도메인 객체는 Lombok 가치 적음

본 프로젝트 도메인 = `final class` + 정적 팩토리 + `record` VO 패턴. 보일러플레이트 자체가 적음. Application Service / Controller 의 Spring DI 생성자 (~5-7 의존성) 같은 큰 보일러플레이트 X.

→ Lombok 도입 가치는 **DI 관련 layer (Service / Adapter / Controller)** 에 집중되고 도메인은 효과 미미.

### D. ArchUnit 규칙 추가

각 서비스 모듈의 `ArchitectureTest.java` 에 다음 규칙 추가 (Step 2 마이그레이션 PR 시점에 일괄 적용):

```java
// R10 — domain 계층은 Lombok 의존 금지 (§C 1번 — 순수 Java 유지)
@ArchTest
static final ArchRule domain_should_not_depend_on_lombok =
    noClasses()
        .that().resideInAPackage("app.backend.jamo.<service>.domain..")
        .should().dependOnClassesThat()
        .resideInAPackage("lombok..")
        .as("domain 계층은 Lombok annotation 을 사용하지 않는다 (ADR-0008 §C)");

// R11 — @Data / @Setter / @AllArgsConstructor / @NoArgsConstructor 전 layer 금지 (§B 블랙리스트)
@ArchTest
static final ArchRule no_data_or_setter_annotation =
    noClasses()
        .should().beAnnotatedWith("lombok.Data")
        .orShould().beAnnotatedWith("lombok.Setter")
        .orShould().beAnnotatedWith("lombok.AllArgsConstructor")
        .as("Lombok @Data/@Setter/@AllArgsConstructor 는 도메인 캡슐화 / DTO 불변성 우회 위험으로 금지 (ADR-0008 §B)");
```

`@NoArgsConstructor` 는 JPA Entity 의 protected no-arg constructor 가 필요할 수 있어 (Hibernate spec) 전역 차단 X — 도메인 layer R10 으로 이미 차단됨.

R8 (ai.proto direct import 차단) 다음 번호 R10 / R11 부여.

## 결과 및 영향

### 즉시 (본 ADR PR)

- ADR-0008 박제. `_index.md` 1줄 추가.
- 코드 변경 0.

### 후속 (Step 2 마이그레이션 PR)

- 루트 `build.gradle.kts` (subprojects 공통) 또는 각 서비스 `build.gradle.kts` 에 Lombok 의존성 추가.
- identity-service 의 application/service / presentation/controller / infrastructure/grpc 등 명시적 생성자 → `@RequiredArgsConstructor` 마이그레이션. 단 일괄 변경 + 빌드 / 테스트 통과 확인.
- diary-service 의 PR #64 머지 후 application Service 동일 마이그레이션.
- 모든 서비스 `ArchitectureTest.java` 에 R10 / R11 규칙 추가.
- common-auth-jwt / common-infrastructure 도 적용 가능 (코드 표면이 적어 효과 미미하지만 일관성).

### 후속 (점진적 — 새 코드 작성 시점부터)

- 본 ADR 머지 후 작성되는 모든 신규 Service / Controller / Adapter 는 `@RequiredArgsConstructor` 사용.
- JPA Entity (Infrastructure layer) 추가 시 `@Getter` 사용. record 미사용 (JPA spec).

### 운영 / 관측

- annotation processor 단계 추가로 빌드 시간 약간 증가 (수 초).
- IDE Lombok plugin 필수 — 개발자 환경 가이드에 명시 (별 README 또는 docs/ 영역 — Step 2 시점).
- `ContractsArchitectureTest` (contracts 모듈) 도 R10 / R11 적용 검토 — contracts 는 contracts/event/* 가 record 라 Lombok 가치 X. 본 결정 시점 미적용.

### 결정 대기 항목 (별도 결정 시점)

- **`@Builder` 도입 시점** — 본 ADR 시점 화이트리스트에 포함되나 실제 사용은 7+ 필드 DTO 등장 시. 현 시점 sentence-feedback 영역의 5 DTO 는 모두 record (Builder 불요).
- **`@Slf4j` 적용 범위** — Application Service 의 로깅 / Controller 의 access log / Infrastructure Adapter 의 외부 호출 로깅. 운영 PR 시점 결정.
- **테스트 코드의 Lombok 사용** — JUnit 테스트의 `@RequiredArgsConstructor` (의존 fixture 주입) 는 본 ADR 화이트리스트에 미포함. 후속 검토.
- **Lombok 버전 관리 전략** — Spring Boot dependency-management 가 자동 해석 vs 명시 버전 고정. Step 2 시점 결정.

## 참고

- Eric Evans, *Domain-Driven Design* — Layers / Pure POJO Domain
- Vaughn Vernon, *Implementing Domain-Driven Design* (IDDD) Ch.2 "Layers", Ch.10 "Aggregates"
- [CLAUDE.md](../../CLAUDE.md) "핵심 원칙 #1, #2" — 도메인 객체 ≠ DB Entity / 의존성 안쪽 방향
- [`.claude/skills/ddd-architecture/SKILL.md`](../../.claude/skills/ddd-architecture/SKILL.md) — Layered 패턴 명시
- [ADR-0005 JPA 연관관계 금지](0005-no-jpa-associations.md) — 도메인 순수성 정책 인접
- Lombok 공식 docs — https://projectlombok.org/features/
- Alistair Cockburn, *Hexagonal Architecture* (검토 후 거부 — 본 ADR §A2 trade-off)
