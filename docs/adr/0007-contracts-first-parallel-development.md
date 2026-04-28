# ADR-0007: Contracts-First 병렬 개발 — Phase 6 진입 시점에 채택

- **상태**: Accepted
- **결정일**: 2026-04-28
- **결정자**: jonghun
- **관련 ADR**: [ADR-0002 서비스 분할](0002-service-decomposition.md), [ADR-0003 AI 호출 아키텍처](0003-ai-call-architecture.md), [ADR-0004 contracts 명명·버전](0004-contracts-naming-and-versioning.md)
- **관련 결정**: [identity/user-profile-domain-boundary.md](../decisions/identity/user-profile-domain-boundary.md)

## 컨텍스트

PR6 시리즈 (user LOCAL 가입) + PR #29 (로컬 dev 인프라 + swagger) + PR #30 (`_status.md` 갱신) 머지 직후, identity-service profile 도메인 (Phase 6) 진입 직전에 **다른 서비스 (diary / chat / learning / platform / ai-service) 와 병렬 진행 가능성** 을 분석했다. 결과는 다음 4가지 사실로 요약된다.

1. **다른 모든 서비스는 placeholder 단계** — `<service>/src/main/java/.../Application.java` + `.gitkeep` 만. application.yaml 도 `spring.application.name` 만.
2. **contracts 모듈은 완전히 비어 있음** — `contracts/src/main/proto/.gitkeep` 만, Kafka 이벤트 record 도 동일. 즉 서비스간 통신 계약이 0건.
3. **profile 응답 스키마는 backend 서비스간 영향이 없다** — 다른 서비스는 `user_id` 외래 ID 만 보유하고 identity 도메인 데이터를 직접 조회하지 않는다 (CLAUDE.md 핵심원칙 #3). 사용자 표시명은 platform-service 가 `UserSummaryService` gRPC 로 조회 (ADR-0002 §평가 5). 따라서 profile 응답 boundary 결정 ([decisions/identity/user-profile-domain-boundary.md](../decisions/identity/user-profile-domain-boundary.md)) 이 끝난 시점에 backend 작업의 차단 요소는 사라졌다.
4. **공통 인프라 (common-auth-jwt + common-infrastructure + 로컬 docker compose) 는 이미 완비** — JWT 검증 / MySQL / Redis / Flyway 가 즉시 사용 가능.

이 시점이 "병렬 가능 시점 포인트" 이다. 더 일찍 (예: PR3 OAuth 끝났을 때) 시도했다면 profile boundary 결정이 안 되어 응답 스키마 불확실성이 있었고, 더 늦게 (예: profile 끝나고) 시도했다면 contracts 가 여전히 비어 있어 동일한 차단을 1주일 뒤에 풀게 됐을 것이다.

## 검토한 옵션

| 옵션 | 장점 | 단점 |
|---|---|---|
| **A. Sequential (기존 패턴 유지)** — profile 끝낸 뒤 contracts → diary → chat ... 순 | 단순. 컨텍스트 한 번에 1트랙. 기존 PR 페이스 (32m/PR) 그대로 유지 | 진행 속도 느림. 1인 개발자 = 1 동시 트랙이라 트랙 간 hand-off 비용은 어차피 발생. contracts 작업이 매번 차단 트리거 |
| **B. Contracts-First Parallelization** — 1-2 PR 로 proto 3종 + 이벤트 record 5-7종 정의 후, profile + diary domain skeleton 병렬 | 다른 서비스 차단 해제. PR 슬라이스 단위로 도메인 인터리빙 가능 (오늘 profile / 내일 diary domain 식). contracts 정의가 모든 서비스에 한 번에 박혀 추후 재작업 0 | 상류 1-2 PR 의존 (proto/event 정의 PR 머지 전엔 다른 서비스 시작 못함). ADR-0003 Open Item (Java/Python proto 빌드 동기화) 결정 부담 |
| **C. Aggressive Parallelization** — contracts 없이 mock 으로 다 시작 | 가장 빠른 출발 | contracts 정의 시 모든 서비스 재작업 (gRPC 스텁 / 이벤트 record 사용처 전부). integration debt 큼. 기존 결정 (CLAUDE.md NEVER) 위반 |

## 결정 — **B 채택**

**Phase 6 (profile) 진입 직전에 contracts 채우기를 선행하고, 그 후 profile 트랙과 diary 트랙을 병렬 진행**한다.

### 선행 PR 시퀀스 (1-2 PR, 추정 1-2h)

1. **contracts proto 3종 정의** — `ai.proto` (`AiService.complete/speechToText/textToSpeech`), `chat.proto` (`AiAssistantService.requestSentenceFeedback/validateDiaryContent` 등), `identity.proto` (`UserSummaryService`). [`docs/architecture/contracts-catalog.md`](../architecture/contracts-catalog.md) 의 카탈로그에서 인터페이스 도출.
2. **contracts Kafka 이벤트 record 5-7종** — `ActivityHappened` / `UserWithdrawalRequested` / `UserDataPurged` / `DiaryCreated` / `CommentCreated` / `ChatGenerated` / `VoiceInputProcessed`. ADR-0004 의 명명·버전 규칙 준수.
3. **(선택) `common-auth-web` 모듈 분리** — `@LoginUser` ArgumentResolver. diary 시작 시점에 함께 해도 OK ([_status.md "후속 별도 PR" PR4-c Code M3](../prd/_status.md)).

### 병렬 트랙 (선행 PR 머지 후)

| Track | 범위 | 우선순위 |
|---|---|---|
| **A — identity profile** | Phase 6, 4 API (getMyProfile / getProfile / updateMyProfile / listSavedClips) | 그대로 진행 |
| **B — diary-service** | PRD 일괄 평가 (KEEP/FIX/DROP) → domain skeleton (Aggregate / VO / 도메인 예외) → diary/create.md 부터 슬라이스 | 동시 출발. diary 가 가장 큰 도메인 (24 API) 이라 평탄화 효과 큼 |

다른 서비스 (chat / learning / platform) 는 두 트랙 진행 중 PRD 평가 / domain skeleton 까지 인터리빙 가능. 단 chat 의 AI 게이트웨이 본격 구현은 ai-service 와 묶어 진행 (contracts 검증 효과).

## 근거

### 왜 이 시점인가
- **트리거 조건 4개가 동시 충족**: (1) identity auth+user 가입 완료 (2) profile boundary 결정 박힘 (3) common-auth-jwt + 인프라 완비 (4) 다른 서비스가 placeholder 라 재작업 비용 0
- 더 일찍은 (2)·(4) 미충족, 더 늦으면 contracts 차단을 1주일 뒤에 동일하게 풀어야 함

### 왜 Option C (mock-first) 가 아닌가
- CLAUDE.md NEVER: "다른 서비스 모듈의 Domain / JpaEntity / Repository import 금지" 와 동치인 "통신 계약 없는 임시 import" 패턴은 결국 contracts 정의 시 전체 재작업
- Option B 의 선행 1-2 PR 비용 < Option C 의 누적 재작업 비용

### 왜 Option A (sequential) 가 아닌가
- 1인 개발자라 동시 코딩은 1트랙이지만, PR 슬라이스 단위 인터리빙은 sequential 대비 약 30-50% 시간 절약 가능 (도메인 단위 평탄화)
- contracts 정의를 미루는 것은 단순 지연이 아니라 **매 PR 마다 재발하는 차단** — proto 1개 만들 때마다 사용 서비스 PR 이 대기

## 결과 및 영향

### 즉시 영향
- 다음 PR 은 **contracts proto 3종** (또는 `ai.proto` 부터 1-PR 분할). ADR-0003 의 Open Item (Java/Python proto 빌드 동기화 자동화 — Gradle task / Makefile / pre-commit) 도 같은 PR 에서 결정.
- 그 다음 PR 은 **contracts 이벤트 record** 5-7종.
- 그 후 profile (Track A) + diary (Track B) 병렬 시작.

### 운영 / 페이스 영향
- contracts 채우기 추정: 30m-1h × 2 PR ≈ 1-2h
- 병렬 진입 후 다른 서비스 첫 PR 진행 시 CLAUDE.md "새 서비스 컨테이너화 의무" + "OpenAPI 문서화 의무" 준수 — Dockerfile / docker-compose entry / `<service>/build.gradle.kts springdoc` 같은 PR 에 포함 (PR #29 에서 의무화됨)
- `_status.md` 단계 표 에 `(별도) contracts 선행` 행 추가 후 두 트랙 병렬 진행 기록

### 리스크 / 모니터링
- contracts 변경은 모든 서비스 빌드에 영향 — 한 번에 정의 후 안정화 우선. ADR-0004 의 breaking change 규칙 (field number `reserved`, 새 버전 클래스) 준수.
- E2E Redis SET TTL 환경 이슈 ([_status.md "후속 별도 PR (infra)"](../prd/_status.md)) 가 다른 서비스 E2E 작성 시 동일 재현 가능 — root cause 분석 우선순위 상향 검토 필요.
- 1인 동시 트랙 컨텍스트 스위치 비용 — 도메인 단위 끊어 가는 패턴 (PR5-d/PR6-c 에서 검증된 32m/PR 페이스) 유지 필요. 컨텍스트 스위치가 페이스를 무너뜨리면 sequential 로 회귀.

## 후속 결정

- **ADR-0003 Open Item — Java↔Python proto 빌드 동기화** 자동화: contracts proto 첫 PR 에서 결정 + 보강.
- contracts proto 정의 후 **각 서비스 placeholder 졸업 PR** 에서 docker compose entry / Dockerfile / springdoc 의무 규칙 준수 검증.
- diary 진입 시점에 **`common-auth-web` 모듈 분리** PR 처리 (또는 contracts 선행 시퀀스에 포함).

## 참고

- [`docs/architecture/service-domain-mapping.md`](../architecture/service-domain-mapping.md) — 서비스 의존 그래프 / 호출 흐름
- [`docs/architecture/contracts-catalog.md`](../architecture/contracts-catalog.md) — proto / 이벤트 카탈로그 (정의 대상 인터페이스)
- [`docs/prd/_status.md`](../prd/_status.md) — PR 페이스 / 후속 별도 PR 항목
