# CLAUDE.md

jamo-backend 저장소에서 Claude Code 가 지켜야 할 **최상위 규칙**. 세부 지침은 `.claude/skills/` 와 `.claude/agents/` 에서 계층적으로 제공된다.

---

## 프로젝트

- **스택**: Java 21, Spring Boot 3.5, Gradle Kotlin DSL, **멀티모듈** (모노레포)
- **아키텍처**: MSA + 서비스별 DDD Layered
- **서비스**: Java 5 + Python 1 (ai-service) — 자세한 매핑은 [ADR-0002](docs/adr/0002-service-decomposition.md), [ADR-0003](docs/adr/0003-ai-call-architecture.md), [`docs/architecture/service-domain-mapping.md`](docs/architecture/service-domain-mapping.md)
- **서비스간 통신**: 동기 **gRPC** (`grpc-spring-boot-starter`, Java↔Java + Java↔Python), 비동기 **Kafka** (Outbox 패턴)
- **DB**: 단일 **MySQL** 인스턴스 + Java 서비스당 스키마 분리 (Database per Service 정신). 테스트는 Testcontainers(MySQL)
- **캐시**: Redis (토큰 블랙리스트 / 인증코드 TTL / 활동 랭킹 ZSET / Read Model)
- **인증**: OAuth2(KAKAO/NAVER/GOOGLE) + LOCAL + JWT(RS256) + PKCE — [ADR-0001](docs/adr/0001-authentication-architecture.md)
- **AI**: chat-service(Java) ↔ ai-service(Python, FastAPI+grpcio) gRPC — [ADR-0003](docs/adr/0003-ai-call-architecture.md)
- **테스트**: JUnit 5, AssertJ, Mockito, Spring Boot Test, Testcontainers

---

## 서비스 구조 (요약)

| 서비스 | 언어 | 포함 도메인 | MySQL 스키마 |
|---|---|---|---|
| identity-service | Java | auth, user, profile | `identity` |
| diary-service | Java | diary, comment, validation, diarychat, sentence-feedback | `diary` |
| chat-service | Java | chat (14 API) + AI 비즈니스 게이트웨이 | `chat` |
| learning-service | Java | sentence, word (비배포 시작) | `learning` |
| platform-service | Java | shorts, event(랭킹), feedback | `platform` |
| **ai-service** | Python | LLM / STT / TTS 추론. chat-service 만 호출 | (없음, stateless) |

자세한 의존 그래프 / 호출 흐름 → [`docs/architecture/service-domain-mapping.md`](docs/architecture/service-domain-mapping.md)

---

## 핵심 원칙 (NEVER 위반)

1. **도메인 객체 ≠ DB Entity.** 도메인에 JPA/Spring 어노테이션 금지. JPA 는 `infrastructure/persistence/entity/XxxJpaEntity` 에 격리하고 Mapper 로 변환.
2. **의존성은 항상 안쪽으로.** Domain ← Application ← Infrastructure / Presentation. Domain 은 어떤 프레임워크에도 의존하지 않는다.
3. **한 서비스 = 하나의 Bounded Context = 하나의 MySQL 스키마.** 다른 서비스의 Domain / JpaEntity / Repository 를 직접 import 금지. 다른 서비스 데이터는 gRPC 호출 또는 Kafka 이벤트 구독으로만.
4. **서비스간 공유는 `:contracts` 모듈을 통해서만.** proto + Kafka 이벤트만. Aggregate / JpaEntity 금지.
5. **AI 호출은 chat-service 단일 진입점.** 다른 Java 서비스는 chat-service 의 `AiAssistantService` (gRPC) 만 호출. ai-service 는 chat-service 만 호출. 사용자 직접 호출 X.
6. **계층별 테스트는 독립적.** Domain 은 Spring 없이, Application 은 Repository Mock 으로, Infrastructure 는 `@DataJpaTest`, Presentation 은 `@WebMvcTest`.
7. **테스트 없는 코드는 머지 금지.** 모든 public 메서드와 분기는 테스트로 커버.
8. **계획 없이 구현 금지.** 요구사항을 받으면 계획을 수립하고 사용자 승인을 받은 뒤 착수한다.
9. **PR 머지 대상은 dev 브랜치** (main 아님). 새 작업 시작 전 `git checkout dev && git pull origin dev`.

---

## 워크플로우

각 단계에서 해당 **스킬(📘)** 을 참조하고, **서브 에이전트(🤖)** 는 조건에 맞을 때 반드시 호출한다.

```
[요구사항]
    ↓
[계획]            📘 code-planning
    ↓
[설계 검증]       🤖 ddd-architect           (새 Aggregate / 모델 큰 변경 시)
    ↓
[구현]            📘 ddd-architecture        (단일 서비스 내부 4계층)
                  📘 module-boundary         (서비스간 통신 / contracts / Saga / Outbox / Read Model)
    ↓
[테스트]          📘 testing-junit
    ↓
[리뷰]            🤖 code-reviewer + 🤖 test-reviewer  (병렬)
                  🤖 security-reviewer        (인증 / 결제 / PII / 파일 / 관리자 시)
    ↓
[문서]            📘 documentation
    ↓
[커밋]            📘 commit-convention
    ↓
[PR]              📘 pr-guidelines           (PR base 는 dev)
```

**판정 대응 규칙**: 에이전트가 `NEEDS CHANGES` / `REJECT` / `BLOCK` 을 반환하면 다음 단계로 진행하지 않고 수정 후 같은 에이전트를 재호출한다. 임의 판단으로 건너뛰지 않는다.

---

## PRD 참조 규칙

새로운 기능 요청을 받으면 `code-planning` 스킬의 Step 1 직전에:

1. `docs/prd/` 디렉토리를 먼저 확인한다 (`Glob`, `Grep` 활용). 진행 상태 추적은 [`docs/prd/_status.md`](docs/prd/_status.md).
2. 요청과 관련된 PRD 파일을 찾아 **전체**를 읽는다.
3. 관련 PRD가 있으면:
   - 재진술 시 PRD 의 **Goals / Non-Goals** 를 명시적으로 반영
   - Non-Goals 범위를 침범하는 요청이면 사용자에게 먼저 확인
   - PRD 의 **Open Questions** 가 해결되지 않았으면 구현 전에 질문
   - FR (Functional Requirements) 번호를 TodoList 에 매핑
4. 관련 PRD 가 없으면 사용자에게 "PRD 문서가 있나요?" 확인 후 통상 계획 프로세스 진행.
5. **PRD 규모가 크면 단위 PR 로 분할을 제안**하고 승인받은 뒤에 첫 단위부터 착수한다. 한 번에 전체 구현 금지.

PRD 와 코드 변경이 불일치하면 즉시 지적한다. Non-Goals 구현 시도 또는 FR 누락은 사용자에게 보고.

---

## 디렉토리 구조

### Java 서비스 모듈 내부 (예: `identity-service/src/main/java/app/backend/jamo/identity/`)

```
identity/
├── domain/                     # 순수 도메인 (프레임워크 의존 금지)
│   ├── model/                  # Aggregate Root, Entity, Value Object
│   ├── repository/             # Repository 인터페이스
│   ├── service/                # Domain Service
│   ├── event/                  # Domain Event
│   └── exception/              # Domain 예외
├── application/                # Use Case
│   ├── service/                # Application Service (@Transactional 경계)
│   └── dto/                    # Command / Query / Result
├── infrastructure/             # 기술 세부사항
│   ├── persistence/
│   │   ├── entity/             # JPA Entity (XxxJpaEntity)
│   │   ├── repository/         # Domain Repository 구현
│   │   └── mapper/             # Domain ↔ JpaEntity 변환
│   ├── grpc/
│   │   ├── server/             # @GrpcService (다른 서비스가 호출)
│   │   └── client/             # @GrpcClient (다른 서비스 호출)
│   ├── messaging/              # Outbox 발행 / Kafka Consumer
│   ├── external/               # 외부 시스템 연동 (Domain 인터페이스 구현체)
│   └── config/                 # Spring 설정
└── presentation/               # HTTP 진입점
    ├── controller/
    ├── dto/                    # Request / Response
    └── exception/              # ExceptionHandler
```

테스트는 `src/test/java/...` 에 동일 구조로.

### Python ai-service (`python-services/ai-service/`)

```
ai-service/
├── main.py                     # FastAPI app (REST: health/admin)
├── grpc_server.py              # gRPC AiService (LLM + STT + TTS)
├── ai/
│   ├── llm/                    # OpenAI / vLLM 추상 클라이언트
│   ├── stt/                    # Whisper / OpenAI Whisper API
│   └── tts/                    # TTS 모델 (OpenAI TTS / 자체)
├── proto/                      # contracts/*.proto 에서 생성된 .py
└── pyproject.toml              # uv 또는 poetry
```

### 멀티모듈 루트 (예정)

```
:contracts                      # proto + Kafka 이벤트 (Java + Python 양쪽 빌드 입력)
:common-auth-jwt                # JWT 검증 라이브러리 (ADR-0001)
:common-infrastructure          # MySQL/Redis/Kafka 공통 설정
:identity-service / :diary-service / :chat-service / :learning-service / :platform-service
python-services/ai-service/     # Gradle 빌드 외부, uv/poetry
```

### 로컬 docker 인프라

```
docker-compose.yml              # mysql + redis + (실 코드 보유) Java 서비스
.env.example                    # 환경변수 템플릿 (.env 는 gitignore)
.dockerignore                   # 빌드 컨텍스트 슬림화
docker/
├── README.md                   # 실행 / 새 서비스 추가 절차
├── mysql/init/                 # 5개 스키마 + 서비스별 user 자동 생성
└── scripts/generate-dev-keys.sh # RSA(PKCS#8) + pepper 생성 → .env 붙여넣기
<service>/Dockerfile            # 서비스별 멀티스테이지 빌드 (identity-service 가 레퍼런스)
```

---

## 금지 사항

### 도메인 / 계층
- ❌ 도메인 객체에 JPA/Spring/Jackson 어노테이션
- ❌ public setter (상태 변경은 의미 있는 메서드로)
- ❌ Controller 에서 Repository 직접 호출 (반드시 Application Service 경유)
- ❌ Application Service 가 다른 Application Service 직접 호출 (Domain Service 또는 이벤트로)
- ❌ **JPA 연관관계 어노테이션** (`@ManyToOne` / `@OneToMany` / `@OneToOne` / `@ManyToMany`) — 외래 ID 컬럼만 보유 + 검증은 어플리케이션 로직 (ADR-0005)
- ❌ **DB 레벨 FOREIGN KEY constraint** (`ON DELETE CASCADE` 포함) — 인덱스 (`INDEX idx_<table>_<col>`) 만 명시 (ADR-0005)

### 서비스 경계
- ❌ 다른 서비스 모듈의 Domain / JpaEntity / Repository import
- ❌ 다른 서비스의 MySQL 스키마 직접 JOIN/쿼리
- ❌ contracts 모듈에 Aggregate / JpaEntity / 한 서비스만 쓰는 DTO
- ❌ proto field number 변경 / 의미 변경 (삭제 시 `reserved`)
- ❌ Breaking Change 시 같은 클래스 수정 (새 버전 클래스 `XxxV2`)

### AI 호출
- ❌ ai-service 를 사용자/외부에서 직접 호출
- ❌ chat-service 외 다른 서비스가 ai-service 직접 호출
- ❌ chat-service 가 직접 LLM API 호출 (반드시 ai-service 경유)

### gRPC / Kafka
- ❌ gRPC 호출에 Deadline 미설정
- ❌ Circuit Breaker / Retry / Fallback 미설정 (Resilience4j)
- ❌ Kafka Consumer 멱등성 미처리 (`ProcessedEvent` 테이블 필수)
- ❌ 분산 트랜잭션 (2PC, JTA) 시도 — Saga + 보상 트랜잭션
- ❌ Outbox 없이 도메인 이벤트 발행 (DB 트랜잭션 + Kafka 원자성 위반)

### 테스트 / 코드 품질
- ❌ 테스트 `@Disabled` / `@Ignore` / 회피
- ❌ `printStackTrace()`, `System.out`, 디버그 주석 잔재
- ❌ 매직 넘버 / 매직 스트링 (상수 / Enum 사용)
- ❌ 한 커밋에 여러 관심사 혼합

### 프로세스
- ❌ 사용자 승인 없는 공개 API 시그니처 변경
- ❌ 서브 에이전트 리뷰를 건너뛰고 커밋/PR 진행
- ❌ 리뷰의 Critical/High 지적 임의 무시
- ❌ **main 브랜치로 직접 PR** (반드시 dev 베이스)
- ❌ `build.gradle.kts` 의존성 임의 추가 (사용자 제안 후 승인)
- ❌ **새 Java 서비스가 placeholder 를 벗어나는 PR 에서 `<service>/Dockerfile` / `docker-compose.yml` service entry / `.env.example` 변수 누락** — 같은 PR 에 함께 포함 (아래 "작업 규칙 — 새 서비스 컨테이너화" 참고)

---

## 작업 규칙

- 작업 시작 시 `git checkout dev && git pull origin dev` 후 `feature/<service>-<도메인>-<action>` 브랜치. 예: `feature/identity-auth-oauth-callback`
- **build.gradle.kts 의존성을 임의로 추가하지 않는다.** 필요 시 사용자에게 제안.
- 기존 코드 스타일·네이밍을 우선 따른다.
- 작업 완료 전 빌드 확인:
  - 단일 서비스: `./gradlew :<service>:build`
  - 전체: `./gradlew clean build`
  - ai-service 변경 시: `cd python-services/ai-service && uv run pytest` (예정)
- 추측 금지 — 불명확한 부분은 질문한다.

### 새 서비스 컨테이너화 (의무)

Java 서비스가 placeholder (Application.java + .gitkeep) 를 벗어나 **실제 비즈니스 코드** (controller / repository / domain model 등) 를 처음 갖게 되는 PR 에서는 **같은 PR 에** 다음을 포함한다. 누락 시 NEVER 위반.

1. **`<service>/Dockerfile`** — `identity-service/Dockerfile` 그대로 복사 후 4곳만 치환:
   - `:identity-service:bootJar` → `:<service>:bootJar`
   - `identity-service-*.jar` → `<service>-*.jar`
   - `EXPOSE 8081` → 해당 서비스 포트 (8082 / 8083 / 8084 / 8085)
   - `COPY identity-service ...` → `COPY <service> ...` (의존 모듈 + 본 서비스 src 만)
2. **`docker-compose.yml`** — `identity-service:` 블록 복사 후 `container_name` / `ports` / `environment` prefix / `dockerfile` 경로만 변경. `depends_on: mysql/redis healthy` 유지.
3. **`docker/mysql/init/01-create-schemas.sql`** — 5개 스키마/유저는 이미 준비됨. 새 도메인이 늘 때만 갱신.
4. **`.env.example`** — `<SERVICE>_SERVER_PORT / <SERVICE>_DB_USERNAME / <SERVICE>_DB_PASSWORD` 추가.
5. (Python ai-service 의 경우) `python-services/ai-service/Dockerfile` 별도 — uv 기반, gRPC 9090 포트.

상세 절차: [`docker/README.md`](docker/README.md) "새 서비스 추가 시" 섹션.

---

## 참고

### 내부
- 스킬: [`.claude/skills/`](.claude/skills/) — 단계별 지침
- 에이전트: [`.claude/agents/`](.claude/agents/) — 독립 컨텍스트 리뷰어
- ADR: [`docs/adr/`](docs/adr/) — 의사결정 기록
- PRD: [`docs/prd/`](docs/prd/) — 도메인별 요구사항 명세
- 아키텍처: [`docs/architecture/`](docs/architecture/) — service-domain mapping, contracts catalog
- 로컬 인프라: [`docker/README.md`](docker/README.md) — docker compose 실행 / 새 서비스 컨테이너화 절차

### 외부
- Eric Evans, *Domain-Driven Design*
- Vaughn Vernon, *Implementing Domain-Driven Design*
- Chris Richardson, *Microservices Patterns* (Saga, Outbox)
- OWASP Top 10

---

**마지막 업데이트**: 2026-04-28 (로컬 docker compose 도입 + 새 서비스 컨테이너화 의무 추가)
